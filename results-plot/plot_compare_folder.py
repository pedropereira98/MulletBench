import datetime
import pandas as pd
import matplotlib.pyplot as plt
from pathlib import Path
import re
import os
import glob
from datetime import datetime, timedelta
import matplotlib.font_manager as font_manager
import numpy as np
from collections import defaultdict
import argparse

parser = argparse.ArgumentParser(description="Compare benchmark results across multiple test runs.")
parser.add_argument("data_folder", type=str, help="Path to the folder containing benchmark result subfolders")
parser.add_argument("images_folder", type=str, help="Name of the folder to save generated images. It will be created inside the data_folder if it does not exist.")



# Global storage for aggregated stage statistics
stage_stats = {}

epoch = datetime.utcfromtimestamp(0)
images_folder = "results/Resource_Det_Mixed_17/Aggregation/images_stage_lines/"
data_folder = "results/Resource_Det_Mixed_17/Aggregation/"
metrics_folder = "metrics/"
monitoring_folder = "monitoring/"
os.makedirs(images_folder, exist_ok=True)
os.makedirs(images_folder+metrics_folder, exist_ok=True)
os.makedirs(images_folder+monitoring_folder, exist_ok=True)

DATE_FORMAT = "%Y-%m-%d %H:%M:%S.%f"
# FORMAT="pdf"
FORMAT="png"

# CUT = True
CUT = True
axis1_min = 0
axis1_max = 3000
axis2_min = 15000
axis2_max = 18000

finishes = {}
current_stage = 0 #works if 

PLOT_INDIVIDUAL_CLIENTS = True
PLOT_AGGREGATE_CLIENTS = True
PLOT_DB_MONITORING = True

IGNORE_FIRST = False

plt.rcParams['font.family'] = ['NewsGotT'] #font for thesis
max_test_timestamp = 0

def unix_time_millis(dt):
    return (dt - epoch).total_seconds() * 1000.0

def set_axis(y_vals, x_vals):
    ax = plt.gca()
    if not CUT:
        ax.set_ylim((0, y_vals.max()))
        ax.set_xlim((0, x_vals.max()))

def save(file_path: str, subfolder:str, append: str = ""):
    filename = Path(file_path).stem + append + "." + FORMAT
    print("Saving " + filename)
    figure = plt.gcf()
    # figure.set_size_inches(6.5, 4.3)
    figure.set_size_inches(6.5, 3)

    plt.savefig(images_folder + subfolder + filename, dpi=120, format=FORMAT, bbox_inches="tight")
    plt.clf()

# gets easier to read name for interface columns
def get_interface_columns(monitor_df):
    interface_re = re.compile("network\.interface\.(in|out)\.bytes-(.*)")
    interface_columns = []
    for column in range(0,4):
        matched = interface_re.match(monitor_df.columns[column+1])
        interface_columns.append(matched.group(2) + "-" + matched.group(1))
    
    return interface_columns

def scan_client_stages(run_path):
    local_stages_min = []
    local_stages_max = []
    
    local_current_stage = 0
    
    # find all client files for this run
    client_files = glob.glob("client*.csv", root_dir=run_path)
    
    if not client_files:
        return []
    
    for client_file in client_files:
        try:
            df = pd.read_csv(os.path.join(run_path, client_file)) # (before, after, amount, type)
            df.columns = ['before', 'after', 'amount', 'type']
            df['before'] = pd.to_datetime(df['before'], unit='ns')
            df['after'] = pd.to_datetime(df['after'], unit='ns')
            
            t_min = df['before'].min()
            t_max = df['after'].max()
            
            # Initialize first stage if empty
            if len(local_stages_max) == 0:
                local_stages_min.append(t_min)
                local_stages_max.append(t_max)
                continue
            
            found = False
            # check if this file belongs to an existing stage (overlap/proximity)
            for i in range(local_current_stage + 1):
                # 5 second tolerance
                if (t_min < local_stages_min[i] + timedelta(seconds=5)) and (t_min > local_stages_min[i] - timedelta(seconds=5)):
                    local_stages_max[i] = max(local_stages_max[i], t_max)
                    found = True
                    break
                
            # If not found, it's a new stage
            if not found:
                local_current_stage += 1
                local_stages_min.append(t_min)
                local_stages_max.append(t_max)

        except Exception as e:
            print(f"Warning: Could not process {client_file} for stage detection: {e}")
            continue
        
    # Calculate relative end times (seconds from start of test)
    if not local_stages_min: 
        return []
    
    start_time = local_stages_min[0]
    relative_ends = []

    # Sort stages by time just in case
    sorted_stages = sorted(zip(local_stages_min, local_stages_max), key=lambda x: x[0])

    for s_min, s_max in sorted_stages:
        diff = (s_max - start_time).total_seconds()
        relative_ends.append(diff)

    return relative_ends

def compute_stage_statistics():
    global stage_stats
    print("Computing stage statistics across all runs...")
    
    run_paths = []
    # Locate all run data folders
    for folder in os.listdir(data_folder):
        if "ignore" in folder: continue
        folder_path = os.path.join(data_folder, folder)
        if os.path.isdir(folder_path) and "images" not in folder:
            for run in os.listdir(folder_path):
                if IGNORE_FIRST and run == "run-1": continue
                
                r_path = os.path.join(folder_path, run, "data/")
                if os.path.isdir(r_path):
                    run_paths.append(r_path)
                    
    collected_stages = defaultdict(list)
    
    for r_path in run_paths:
        stages = scan_client_stages(r_path)
        for i, val in enumerate(stages):
            collected_stages[i].append(val)
            
    # Compute stats for each stage index
    for i, vals in collected_stages.items():
        stage_stats[i] = {
            'min': np.min(vals),
            'mean': np.mean(vals),
            'max': np.max(vals)
        }
        print(f"Stage {i}: Min={stage_stats[i]['min']:.2f}s, Mean={stage_stats[i]['mean']:.2f}s, Max={stage_stats[i]['max']:.2f}s")

# converts timedeltaindex to seconds float with milliseconds
def seconds_millis(timedelta: pd.TimedeltaIndex):
    return timedelta.seconds + timedelta.microseconds / 1_000_000

def parse_monitor_datetime(datetime_str: str):
    return datetime.strptime(datetime_str, DATE_FORMAT)

def monitor_df_from_path(file_path: str, name: str = ""):
    global finishes

    #reading data with format time (datetime), lo-in (float), eth0-in (float), lo-out (float),eth0-out (float), RAM (float), cpu-system (float), cpu-user (float), io-read (float), io-write (float)
    monitor_df = pd.read_csv(file_path)
    

    interface_columns = get_interface_columns(monitor_df)
    print(interface_columns)

    monitor_df.columns = ['time', interface_columns[0], interface_columns[1], interface_columns[2], interface_columns[3], 'RAM', 'cpu-system', 'cpu-user', 'io-read', 'io-write']


    # change time to time elapsed
    monitor_df['time'] = pd.to_datetime(monitor_df['time'])
    minutes_time = monitor_df['time'].min()
    monitor_df['time'] =  seconds_millis((monitor_df['time'] - minutes_time).dt)

    time_max = monitor_df['time'].max()

    finishes[name] = time_max
    
    return monitor_df

def plot_stage_lines():
    ax = plt.gca()

    if len(stage_stats) <= 1:
        return
    
    xlabel = ax.get_xlabel()
    scale = 60.0 if "(m)" in xlabel else 1.0
        
    for i, stats in stage_stats.items():
        vals = [stats['min'], stats['mean'], stats['max']]
        
        colors = ['red', 'red', 'red'] 
        styles = [':', '-', ':'] 
        alphas = [0.5, 1.0, 0.5]
        
        for v, c, s, a in zip(vals, colors, styles, alphas):
            plt.axvline(x=v/scale, c=c, linestyle=s, alpha=a)

        # Add text labels
        # Text for the last stage (Test Finish)
        if i == len(stage_stats)-1:
            plt.text(stats['mean']/scale, 1.02, 'Test finish', ha='center', transform=ax.get_xaxis_transform())
        
        text = ""
        if i == 0: text = "Pre-population"
        elif i == 1: text = "Mixed stage"
        
        # Position text between previous stage and current stage
        prev_mean = 0 if i == 0 else stage_stats[i-1]['mean']
        center_x = (prev_mean + stats['mean']) / 2
        
        # Only plot text if there is space
        if text:
            plt.text(center_x/scale, 1.02, text, ha='center', transform=ax.get_xaxis_transform())
            
def plot_monitoring_compare(file_paths: list[str]):

    monitor_dfs = {}
    
    for sub_file_paths in file_paths:
        id = sub_file_paths.split("/")[-2]

        for file_path in os.listdir(sub_file_paths):
            if IGNORE_FIRST and file_path == "run-1":
                continue
            if os.path.isdir(sub_file_paths + file_path):
                complete_file_path = sub_file_paths + file_path + "/data/"

                monitor_dfs[id] = []
                
                for monitor in glob.glob("monitor-cloud*.csv", root_dir=complete_file_path) + glob.glob("monitor-edge*.csv", root_dir=complete_file_path):
                    
                    monitor_df = monitor_df_from_path(complete_file_path + monitor)
                    monitor_df['cpu-total'] = monitor_df['cpu-system'] + monitor_df['cpu-user']
                    monitor_df['cpu-total'] = monitor_df['cpu-total']
                    
                    monitor_dfs[id].append(monitor_df)
                    
    monitor_dfs_aggregated = {}
    
    for id, monitor_list in monitor_dfs.items():
        
        if len(monitor_list) == 0:
            continue
        
        monitor_dfs_aggregated[id] = pd.concat(monitor_list)
        monitor_dfs_aggregated[id].index = pd.to_timedelta(monitor_dfs_aggregated[id]['time'], unit='ns')
        monitor_dfs_aggregated[id].sort_index(inplace=True)
        monitor_dfs_aggregated[id] = monitor_dfs_aggregated[id].resample("20ns").mean()
        
    monitor_dfs_aggregated = dict(sorted(monitor_dfs_aggregated.items(), key=lambda x: x[0] == "Control" or x[0] == "Reference", reverse=True))
    
    
    # plot cpu usage
    fig, ax = plt.subplots(figsize=(15, 10))
    line_styles = ['-', '--', '-.', ':', (5, (10, 3)), (0, (3, 10, 1, 10)), (0, (3, 10, 1, 10, 1, 10))]
    
    line_styles_copy = line_styles.copy()
    for id, monitor_df in monitor_dfs_aggregated.items():
        ax.plot(monitor_df['cpu-total'], label=id, linestyle=line_styles_copy.pop(0))
        
    ax.set_title("Total CPU usage")
    ax.set_ylabel("% up to 100 * number of cores")
    ax.set_xlabel("Elapsed time (s)")
    ax.legend(loc='upper right')
    ax.ticklabel_format(useOffset=False)

    plt.tight_layout()
    plot_stage_lines()
    save(data_folder + "monitor-aggregate", monitoring_folder, "-cpu")

    #plot line graph with RAM for time

    fig, ax = plt.subplots(figsize=(15, 10))
    line_styles_copy = line_styles.copy()
    for id, monitor_df in monitor_dfs_aggregated.items():
        ax.plot(monitor_df['RAM'], label=id, linestyle=line_styles_copy.pop(0))
    ax.set_title("RAM usage")
    ax.set_ylabel("RAM (MB)")
    ax.set_xlabel("Elapsed time (s)")
    ax.legend(loc='upper right')
    ax.ticklabel_format(useOffset=False)
    


    # set_axis(monitor_df['RAM'] + monitor_df2['RAM'], monitor_df['time'] + monitor_df2['time'])

    plt.tight_layout()
    plot_stage_lines()
    save(data_folder + "monitor-aggregate", monitoring_folder, "-memory")

    #plot line graph with eth0-in for time

    fig, ax = plt.subplots(figsize=(15, 10))
    line_styles_copy = line_styles.copy()
    for id, monitor_df in monitor_dfs_aggregated.items():
        ax.plot(monitor_df['eth0-in'], label=id, linestyle=line_styles_copy.pop(0))
    ax.set_title("Average eth0-in")
    ax.set_ylabel("Throughput (MB/s)")
    ax.set_xlabel("Elapsed time (s)")
    ax.legend(loc='upper right')
    ax.ticklabel_format(useOffset=False)
    
    plt.tight_layout()
    plot_stage_lines()
    save(data_folder + "monitor-aggregate", monitoring_folder, "-eth0-in")
    
    fig, ax = plt.subplots(figsize=(15, 10))
    line_styles_copy = line_styles.copy()
    for id, monitor_df in monitor_dfs_aggregated.items():
        ax.plot(monitor_df['eth0-out'], label=id, linestyle=line_styles_copy.pop(0))
    ax.set_title("Average eth0-out")
    ax.set_ylabel("Throughput (MB/s)")
    ax.set_xlabel("Elapsed time (s)")
    ax.legend(loc='upper right')
    ax.ticklabel_format(useOffset=False)
    
    plt.tight_layout()
    plot_stage_lines()
    save(data_folder + "monitor-aggregate", monitoring_folder, "-eth0-out")

    # plot io read/write throughput
    
    fig, ax = plt.subplots(figsize=(15, 10))
    line_styles_copy = line_styles.copy()
    for id, monitor_df in monitor_dfs_aggregated.items():
        ax.plot(monitor_df['io-read'], label=id, linestyle=line_styles_copy.pop(0))
    ax.set_title("Average io-read")
    ax.set_ylabel("Throughput (MB/s)")
    ax.set_xlabel("Elapsed time (s)")
    ax.legend(loc='upper right')
    ax.ticklabel_format(useOffset=False)
    
    plt.tight_layout()
    plot_stage_lines()
    save(data_folder + "monitor-aggregate", monitoring_folder, "-io-read")
    
    ig, ax = plt.subplots(figsize=(15, 10))
    line_styles_copy = line_styles.copy()
    for id, monitor_df in monitor_dfs_aggregated.items():
        ax.plot(monitor_df['io-write'], label=id, linestyle=line_styles_copy.pop(0))
    ax.set_title("Average io-write")
    ax.set_ylabel("Throughput (MB/s)")
    ax.set_xlabel("Elapsed time (s)")
    ax.legend(loc='upper right')
    ax.ticklabel_format(useOffset=False)
    
    plt.tight_layout()
    plot_stage_lines()
    save(data_folder + "monitor-aggregate", monitoring_folder, "-io-write")


import matplotlib.pyplot as plt

def plot_insert_client(file_path: str, client_dfs, runs_per_client: dict | None = None, coarse_aggregation: bool = False):
    
    # fig, ax = plt.subplots(figsize=(15, 10))
    
    # # Plot scatter latency for inserts
    # for label, df in client_dfs.items():
    #     query_groups = df.groupby('type')
    #     for name, group in query_groups:
    #         color = '#d62728' if 'FAILED' in name else None
    #         sc = ax.scatter(seconds_millis(group.index), group.latency, c=color, label=f"{label} {name}", alpha=0.8, s=4)
    
    # ax.set_title("Insert Latency Comparison")
    # ax.set_xlabel("Elapsed time (s)")
    # ax.set_ylabel("Latency (ms)")
    # ax.legend(loc='upper right')
    # ax.ticklabel_format(useOffset=False)
    
    # plt.tight_layout()
    # save(file_path, metrics_folder, "-insert-latency-comparison")
    
    line_styles = ['-', '--', '-.', ':', (5, (10, 3)), (0, (3, 10, 1, 10)), (0, (3, 10, 1, 10, 1, 10))]
    # Plot throughput for inserts
    fig, ax = plt.subplots(figsize=(8, 5))
    
    line_styles_copy = line_styles.copy()
    for label, df in client_dfs.items():
        query_groups = df.groupby('type')
        if "INSERT" in query_groups.groups:
            resample_time = 80.0
            insert_amount = query_groups.get_group("INSERT")['amount']
            insert_throughput = insert_amount.resample(f"{resample_time}s").sum().map(lambda el: el/resample_time).map(lambda el: (el/runs_per_client[label]) if runs_per_client else el)
            insert_throughput.index = insert_throughput.index.map(lambda el: seconds_millis(el) / 60)
            ax.plot(insert_throughput, label=label, linestyle=line_styles_copy.pop(0))
    
    # ax.set_title("Insertion Throughput Comparison")
    ax.set_xlabel("Elapsed time (m)")
    ax.set_ylabel("Throughput (rows/second)")
    ax.legend(loc='upper right')
    ax.ticklabel_format(useOffset=False, style='plain')
    
    plot_stage_lines()
    plt.tight_layout()
    save(file_path, metrics_folder, "-throughput-comparison")
    
    # plot latency trend for inserts
    fig, ax = plt.subplots(figsize=(8, 5))
    
    line_styles_copy = line_styles.copy()
    for label, df in client_dfs.items():
        query_groups = df.groupby('type')
        if "INSERT" in query_groups.groups:
            insert_latency =  query_groups.get_group("INSERT")['latency']
            latency_mean = insert_latency.resample("50s").mean()
            latency_mean.index = latency_mean.index.map(lambda el : seconds_millis(el) / 60)
            ax.plot(latency_mean, label=label, linestyle=line_styles_copy.pop(0))
            
    ax.set_title("Insertion Latency Trend")
    ax.set_xlabel("Elapsed time (m)")
    ax.set_ylabel("Latency (ms)")
    ax.legend(loc='upper right')
    ax.ticklabel_format(useOffset=False)
    
    plot_stage_lines()
    plt.tight_layout()
    save(file_path, metrics_folder, "-insert-latency-trend")

def plot_query_client(file_path: str, query_groups_per_run):
    
    for t in ["AGGREGATION", "DOWNSAMPLING", "OUTLIER_FILTER"]:
        fig, ax = plt.subplots(figsize=(8, 5))

        style = ['-', '--', '-.', ':', (5, (10, 3)), (0, (3, 10, 1, 10)), (0, (3, 10, 1, 10, 1, 10)), (0, (3, 5, 1, 5, 1, 5))]
        for id, query_groups in query_groups_per_run.items():
            
            for name, group in query_groups:
                
                if t not in name:
                    continue
                
                if 'FAILED' in name:
                    color = '#d62728'
                    name = name.replace(t, "")
                    continue
                else:
                    name = ""
                    color = None
                
                # Downsample the data using mean
                numerics = group.select_dtypes("number").resample("10s").mean()
                strings = group.select_dtypes("object").resample("10s").first()
                
                downsampled_group = pd.concat([numerics, strings], axis=1)

                # interpolate to avoid gaps
                downsampled_group.interpolate(method='linear', inplace=True)

                # print(f"Average latency for {name}{id} {t}: {downsampled_group['latency'].mean()}")
                # Plot the downsampled data
                ax.plot(seconds_millis(downsampled_group.index), downsampled_group.latency, c=color, label=f"{name }{id}", alpha=0.8, linewidth=1, linestyle=style.pop(0))
                # ax.scatter(seconds_millis(group.index), group.latency,c=color, label=f"{name }{id}", alpha = 0.8, s=4)
                # ax.plot(seconds_millis(group.index), group.latency, c=color, label=f"{name }{id}", alpha=0.8, linewidth=1)
                ax.legend(loc="upper right", fontsize='large', labelspacing=0.1, fancybox=True, framealpha=0.5)
    

        # plt.title("Latency per Query Types")
        plt.ylabel("Latency (ms)")
        plt.xlabel("Elapsed time (s)")
        # plt.yscale('log')
        plt.tight_layout()
        save(file_path, metrics_folder, f"-{t.lower()}-query-latency")

def plot_benchmark_client(file_path: str):
    
    clients_per_run = {}
    
    for folder in os.listdir(data_folder):
        if "ignore" in folder:
            continue
        if not "images" in folder and os.path.isdir(data_folder + folder + "/"):
            clients_per_run[folder] = []
            for run in os.listdir(data_folder + folder + "/"):
                if IGNORE_FIRST and run == "run-1":
                    continue
                if os.path.isdir(data_folder + folder + "/" + run + "/"):
                    
                    if file_path in os.listdir(data_folder + folder + "/" + run + "/data/"):
                        clients_per_run[folder].append(data_folder + folder + "/" + run + "/data/" + file_path)
    
    client_df_per_run = {}
    query_groups = {}
    
    for run, file_paths in clients_per_run.items():
        aux = []
        for file_path in file_paths:
            client_df  = pd.read_csv(file_path)

            client_df.columns = ['before', 'after', 'amount', 'type']

            client_df['latency'] = (client_df['after'] - client_df['before']) / 1_000_000

            client_df['after'] = pd.to_datetime(client_df['after'], unit='ns')
            min_after = client_df['after'].min()
            client_df['after'] =  (client_df['after'] - min_after)

            # print(f"Average latency for {file_path}: {client_df['latency'].mean()}")

            client_df.sort_values('after', inplace=True)
            client_df.set_index('after', inplace=True)

            
            aux.append(client_df)

        client_df_per_run[run] = pd.concat(aux)
        client_df_per_run[run].sort_values('after', inplace=True)
        query_groups[run] = client_df_per_run[run].groupby('type')          
        
    # sort the client dfs so that the default one is first
    query_groups = dict(sorted(query_groups.items(), key=lambda x: x[0] == "Control" or x[0] == "Reference", reverse=True))

    if any("INSERT" in query_groups[run].groups.keys() for run in query_groups):
        pass
    else:
        plot_query_client(file_path, query_groups)

def plot_aggregate_benchmark_clients(file_paths):
    clients = {}
    
    for sub_file_paths in file_paths:
        # choosing the first run in each folder for comparison
        id = sub_file_paths.split("/")[-2]
        
        clients[id] = {}
        for file_path in os.listdir(sub_file_paths):

            if IGNORE_FIRST and file_path == "run-1":
                continue

            if os.path.isdir(sub_file_paths + file_path):
                complete_file_path = sub_file_paths + file_path + "/data/"
                
                clients[id][file_path] = []
                for client in glob.glob("client[0-9].csv", root_dir=complete_file_path) + glob.glob("client[0-9][0-9].csv", root_dir=complete_file_path):
                    clients[id][file_path].append(complete_file_path + client)
    
    runs_per_client = {id: len(runs) for id, runs in clients.items()}
    
    min_afters = {}
    insert_client_dfs = {}

    for id, runs in clients.items():
        
        min_afters[id] = {}
        
        for run, file_paths in runs.items():
            
            min_after = pd.Timestamp.max

            for file_path in file_paths:

                client_df  = pd.read_csv(file_path)

                client_df.columns = ['before', 'after', 'amount', 'type']
                query_groups = client_df.groupby('type')
                if "INSERT" in query_groups.groups.keys():
                    client_df['after'] = pd.to_datetime(client_df['after'], unit='ns')
                    min_after = min(client_df['after'].min(),min_after)
            
            min_afters[id][run] = min_after
        
    for id, runs in clients.items():
        insert_client_dfs[id] = []
        
        for run, file_paths in runs.items():
            for file_path in file_paths:
                client_df  = pd.read_csv(file_path)

                client_df.columns = ['before', 'after', 'amount', 'type']
                query_groups = client_df.groupby('type')
                if "INSERT" in query_groups.groups.keys():

                    client_df['latency'] = (client_df['after'] - client_df['before']) / 1_000_000

                    client_df['after'] = pd.to_datetime(client_df['after'], unit='ns')
                    client_df['after'] =  (client_df['after'] - min_afters[id][run])

                    client_df.sort_values('after', inplace=True)
                    client_df.set_index('after', inplace=True)

                    insert_client_dfs[id].append(client_df)
                    insert_amount =  query_groups.get_group("INSERT")['amount']
                    insert_throughput = insert_amount.resample("10s").sum()

                    insert_throughput = insert_throughput/10

                    insert_throughput.index = insert_throughput.index.map(lambda el : seconds_millis(el))
                    
                    # print(f"Average throughput for {file_path}: {insert_throughput.mean()}")


    if all(len(insert_client_dfs[id]) > 0 for id in insert_client_dfs):

        insert_clients_dfs_transformed = {}
        
        for id, insert_client_df in insert_client_dfs.items():
            insert_clients_dfs_transformed[id] = pd.concat(insert_client_df)

        for id, insert_clients_df in insert_clients_dfs_transformed.items():
            insert_clients_df.sort_values('after', inplace=True)
            
            numerics = insert_clients_df.select_dtypes("number").resample("10s").mean() 
            strings = insert_clients_df.select_dtypes("object").resample("10s").first()
            
            insert_clients_df = pd.concat([numerics, strings], axis=1)
            
        # set order of clients so it is default first and then rest
        insert_clients_dfs_transformed = dict(sorted(insert_clients_dfs_transformed.items(), key=lambda x: x[0] == "Control" or x[0] == "Reference", reverse=True))

        plot_insert_client(data_folder + "aggregate", insert_clients_dfs_transformed, runs_per_client, coarse_aggregation=True)
    


def main():
    global data_folder, images_folder
    args = parser.parse_args()

    if args.data_folder and args.images_folder:
        if not os.path.isdir(args.data_folder):
            raise ValueError(f"Data folder does not exist: {args.data_folder}")
        data_folder = args.data_folder
        images_folder = os.path.join(data_folder, args.images_folder)
        
        if data_folder[-1] != "/":
            data_folder += "/"
        if images_folder[-1] != "/":
            images_folder += "/"
        os.makedirs(images_folder, exist_ok=True)
        os.makedirs(images_folder+metrics_folder, exist_ok=True)
        os.makedirs(images_folder+monitoring_folder, exist_ok=True)
    elif (not args.data_folder) ^ (not args.images_folder):
        raise ValueError("Both data_folder and images_folder arguments must be provided together.")

    font_manager.fontManager.addfont('NewsGotT.ttf')
    
    compute_stage_statistics()
    
    print(stage_stats)
    
    if PLOT_INDIVIDUAL_CLIENTS:
        
        print("Plotting individual clients")
        
        clients = []
        
        for folder in os.listdir(data_folder):
            if "ignore" in folder:
                continue
            if not "images" in folder and os.path.isdir(data_folder + folder + "/"):
                for run in os.listdir(data_folder + folder + "/"):
                    # verify if it is a directory
                    if not os.path.isdir(data_folder + folder + "/" + run + "/"):
                        continue
                    path = data_folder + folder + "/" + run + "/data/"
                    for file_path in glob.glob("client*.csv", root_dir=path):
                        clients.append(file_path)
        
        for file_path in clients:
            plot_benchmark_client(file_path)
        
    
    if PLOT_AGGREGATE_CLIENTS:
        file_paths = []

        print("Plotting aggregate clients")
        
        for folder in os.listdir(data_folder):
            if "ignore" in folder:
                continue
            if not "images" in folder and os.path.isdir(data_folder + folder + "/"):
                file_paths.append(data_folder + folder + "/")        

        plot_aggregate_benchmark_clients(file_paths)

    if PLOT_DB_MONITORING:
        print("Plotting db monitoring")
        
        file_paths = []
        
        for folder in os.listdir(data_folder):
            if "ignore" in folder:
                continue
            if not "images" in folder and os.path.isdir(data_folder + folder + "/"):
                file_paths.append(data_folder + folder + "/")
                
        plot_monitoring_compare(file_paths)        

if __name__ == "__main__":
    main()
