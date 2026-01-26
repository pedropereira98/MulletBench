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
# FORMAT="png"
FORMAT="pdf"

# CUT = True
CUT = True
axis1_min = 0
axis1_max = 3000
axis2_min = 15000
axis2_max = 18000

finishes = {}
current_stage = 0 #works if 

PLOT_INDIVIDUAL_CLIENTS = False
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
    try:
        monitor_df = pd.read_csv(file_path)
    except Exception as e:
        print(f"Error reading {file_path}: {e}")
        exit(1)

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
            x_pos = stats['mean']/scale
            if x_pos > ax.get_xlim()[1]:
                x_pos = stats['min'] / scale
            plt.text(x_pos, 1.02, 'Test finish', ha='center', transform=ax.get_xaxis_transform())
        
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
        monitor_dfs_aggregated[id] = monitor_dfs_aggregated[id].resample("15ns").mean()
        
    monitor_dfs_aggregated = dict(sorted(monitor_dfs_aggregated.items(), key=lambda x: (x[0].lower() not in ["control", "reference"], x[0].lower())))
    
    
    # plot cpu usage
    fig, ax = plt.subplots(figsize=(15, 10))
    # line_styles = ['-', '--', '-.', ':', (5, (10, 3)), (0, (3, 10, 1, 10)), (0, (3, 10, 1, 10, 1, 10))]
    line_styles = ['-' for _ in range(7)]
    
    line_styles_copy = line_styles.copy()
    for id, monitor_df in monitor_dfs_aggregated.items():
        ax.plot(monitor_df['cpu-total'], label=id, linestyle=line_styles_copy.pop(0))
        
    # ax.set_title("Total CPU usage")
    ax.set_ylabel("% up to 100 * number of cores")
    ax.set_xlabel("Elapsed time (s)")
    ax.set_xlim(left= 0)
    ax.set_ylim(top=ax.get_ylim()[1]*1.2)
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
    # ax.set_title("RAM usage")
    ax.set_ylabel("RAM (MB)")
    ax.set_xlabel("Elapsed time (s)")
    ax.set_xlim(left= 0)
    ax.set_ylim(top=ax.get_ylim()[1]*1.2)
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
    # ax.set_title("Average eth0-in")
    ax.set_ylabel("Throughput (MB/s)")
    ax.set_xlabel("Elapsed time (s)")
    ax.set_xlim(left= 0)
    ax.set_ylim(top=ax.get_ylim()[1]*1.2)
    ax.legend(loc='upper right')
    ax.ticklabel_format(useOffset=False)
    
    plt.tight_layout()
    plot_stage_lines()
    save(data_folder + "monitor-aggregate", monitoring_folder, "-eth0-in")
    
    fig, ax = plt.subplots(figsize=(15, 10))
    line_styles_copy = line_styles.copy()
    for id, monitor_df in monitor_dfs_aggregated.items():
        ax.plot(monitor_df['eth0-out'], label=id, linestyle=line_styles_copy.pop(0))
    # ax.set_title("Average eth0-out")
    ax.set_ylabel("Throughput (MB/s)")
    ax.set_xlabel("Elapsed time (s)")
    ax.set_xlim(left= 0)
    ax.set_ylim(top=ax.get_ylim()[1]*1.2)
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
    # ax.set_title("Average io-read")
    ax.set_ylabel("Throughput (MB/s)")
    ax.set_xlabel("Elapsed time (s)")
    ax.set_xlim(left= 0)
    ax.set_ylim(top=ax.get_ylim()[1]*1.2)
    ax.legend(loc='upper right')
    ax.ticklabel_format(useOffset=False)
    
    plt.tight_layout()
    plot_stage_lines()
    save(data_folder + "monitor-aggregate", monitoring_folder, "-io-read")
    
    ig, ax = plt.subplots(figsize=(15, 10))
    line_styles_copy = line_styles.copy()
    for id, monitor_df in monitor_dfs_aggregated.items():
        ax.plot(monitor_df['io-write'], label=id, linestyle=line_styles_copy.pop(0))
    # ax.set_title("Average io-write")
    ax.set_ylabel("Throughput (MB/s)")
    ax.set_xlabel("Elapsed time (s)")
    ax.set_xlim(left= 0)
    ax.set_ylim(top=ax.get_ylim()[1]*1.2)
    ax.legend(loc='upper right')
    ax.ticklabel_format(useOffset=False)
    
    plt.tight_layout()
    plot_stage_lines()
    save(data_folder + "monitor-aggregate", monitoring_folder, "-io-write")

def plot_insert_client(file_path: str, client_dfs, runs_per_client: dict | None = None, coarse_aggregation: bool = False):

    line_styles = ['-', '--', '-.', ':', (5, (10, 3)), (0, (3, 10, 1, 10)), (0, (3, 10, 1, 10, 1, 10))]
    # Plot throughput for inserts
    fig, ax = plt.subplots(figsize=(8, 5))
    
    line_styles_copy = line_styles.copy()
    for label, df in client_dfs.items():
        query_groups = df.groupby('type')
        if "INSERT" in query_groups.groups:
            resample_time = 40.0
            insert_amount = query_groups.get_group("INSERT")['amount']
            insert_throughput = insert_amount.resample(f"{resample_time}s").sum().map(lambda el: el/resample_time).map(lambda el: (el/runs_per_client[label]) if runs_per_client else el)
            insert_throughput.index = insert_throughput.index.map(lambda el: seconds_millis(el) / 60)
            ax.plot(insert_throughput, label=label, linestyle=line_styles_copy.pop(0))
    
    # ax.set_title("Insertion Throughput Comparison")
    ax.set_xlabel("Elapsed time (m)")
    ax.set_ylabel("Throughput (rows/second)")
    ax.set_xlim(left= 0, right=ax.get_xlim()[1])
    ax.set_ylim(top=ax.get_ylim()[1]*1.5)
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
            latency_mean = insert_latency.resample("30s").mean()
            latency_mean.index = latency_mean.index.map(lambda el : seconds_millis(el) / 60)
            ax.plot(latency_mean, label=label, linestyle=line_styles_copy.pop(0))
            
    # ax.set_title("Insertion Latency Trend")
    ax.set_xlabel("Elapsed time (m)")
    ax.set_ylabel("Latency (ms)")
    ax.set_xlim(left= 0)
    ax.set_ylim(top=ax.get_ylim()[1]*1.5)
    ax.legend(loc='upper right')
    ax.ticklabel_format(useOffset=False)
    
    plot_stage_lines()
    plt.tight_layout()
    save(file_path, metrics_folder, "-insert-latency-trend")


def collect_latency_records(query_groups_per_run, query_types):
    records = []

    for t in query_types:
        for run_id, query_groups in query_groups_per_run.items():
            for name, group in query_groups:
                if t not in name or "FAILED" in name:
                    continue

                resample_interval = 20 if len(query_groups_per_run) > 3 else 10

                numerics = group.select_dtypes("number").resample(f"{resample_interval}s").mean()
                strings = group.select_dtypes("object").resample(f"{resample_interval}s").first()
                downsampled = pd.concat([numerics, strings], axis=1)
                downsampled.interpolate(method="linear", inplace=True)
                
                records.append(pd.DataFrame({
                    "type": t,
                    "run": run_id,
                    "latency": downsampled["latency"].values,
                    "time": seconds_millis(downsampled.index)
                }))

    return pd.concat(records, ignore_index=True)

def collect_volume_records(query_groups_per_run, query_types):
    records = []

    for t in query_types:
        for run_id, query_groups in query_groups_per_run.items():
            for name, group in query_groups:
                if t not in name or "FAILED" in name:
                    continue

                records.append(pd.DataFrame({
                    "type": t,
                    "run": run_id,
                    "volume": group["amount"].values,
                    "time": seconds_millis(group.index)
                }))

    return pd.concat(records, ignore_index=True)

def collect_failed_query_counts(query_groups_per_run, query_types):
    records = []

    for run_id, query_groups in query_groups_per_run.items():
        for name, group in query_groups:
            for t in query_types:
                if f"FAILED_{t}" in name:
                    records.append({
                        "run": run_id,
                        "type": t,
                        "count": len(group)
                    })

    return pd.DataFrame(records)


def plot_query_line(latency_df, query_type, run_colors, file_path, suffix):
    fig, ax = plt.subplots(figsize=(8, 5))

    subset = latency_df if query_type is None else latency_df[latency_df["type"] == query_type]

    for run, color in run_colors.items():
        run_df = subset[subset["run"] == run]
        if run_df.empty:
            continue

        ax.plot(
            run_df["time"],
            run_df["latency"],
            label=f"{run}",
            color=color,
            linewidth=1,
            alpha=0.85
        )

    ax.set_xlabel("Elapsed time (s)")
    ax.set_ylabel("Latency (ms)")
    ax.legend(fontsize="large", fancybox=True, framealpha=0.5)
    ax.set_xlim(left=0)
    ax.set_ylim(top=ax.get_ylim()[1] * 1.5)

    plt.tight_layout()
    save(file_path, metrics_folder, suffix)

def plot_query_bar_latency(latency_df, query_types, runs, run_colors, file_path, suffix):
    mean_df = latency_df.groupby(["type", "run"])["latency"].mean().reset_index()

    x = np.arange(len(query_types))

    group_width = 0.6
    max_bar_width = 0.25
    bar_width = min(max_bar_width, group_width / len(runs))
    offset = (len(runs) - 1) * bar_width / 2

    plt.figure(figsize=(10, 6))

    for i, run in enumerate(runs):
        values = [
            mean_df[(mean_df["type"] == t) & (mean_df["run"] == run)]["latency"].values[0]
            if not mean_df[(mean_df["type"] == t) & (mean_df["run"] == run)].empty else 0
            for t in query_types
        ]

        plt.bar(
            x + i * bar_width - offset,
            values,
            width=bar_width,
            color=run_colors[run],
            label=f"{run}"
        )

    plt.xticks(x, query_types, fontsize=8)
    xlim = plt.xlim()
    xlim_range = xlim[1] - xlim[0]
    mid = (xlim[1] + xlim[0]) / 2
    if bar_width > 0.4 * xlim_range:
        plt.xlim((mid - xlim_range, mid + xlim_range))
    plt.xlabel("Query type", fontsize=9)
    plt.ylabel("Average latency (ms)", fontsize=9)
    plt.legend(fontsize="large", fancybox=True, framealpha=0.5)
    plt.tight_layout()

    save(file_path, metrics_folder, suffix)
    
def plot_query_bar_volume(volume_df, query_types, runs, run_colors, file_path, suffix):
    mean_df = volume_df.groupby(["type", "run"])["volume"].mean().reset_index()

    x = np.arange(len(query_types))

    group_width = 0.6
    max_bar_width = 0.25
    bar_width = min(max_bar_width, group_width / len(runs))
    offset = (len(runs) - 1) * bar_width / 2

    plt.figure(figsize=(10, 6))

    for i, run in enumerate(runs):
        values = [
            mean_df[(mean_df["type"] == t) & (mean_df["run"] == run)]["volume"].values[0]
            if not mean_df[(mean_df["type"] == t) & (mean_df["run"] == run)].empty else 0
            for t in query_types
        ]

        plt.bar(
            x + i * bar_width - offset,
            values,
            width=bar_width,
            color=run_colors[run],
            label=f"{run}"
        )

    plt.xticks(x, query_types, fontsize=8)
    xlim = plt.xlim()
    xlim_range = xlim[1] - xlim[0]
    mid = (xlim[1] + xlim[0]) / 2
    if bar_width > 0.4 * xlim_range:
        plt.xlim((mid - xlim_range, mid + xlim_range))
    plt.xlabel("Query type", fontsize=9)
    plt.ylabel("Average Volume (records/query)", fontsize=9)
    plt.legend(fontsize="large", fancybox=True, framealpha=0.5, loc="upper right")
    plt.tight_layout()

    save(file_path, metrics_folder, suffix)

def plot_failed_queries_bar(failed_df, query_types, runs, run_colors, file_path, suffix):

    if failed_df.empty:
        return

    counts = (
        failed_df
        .groupby(["type", "run"])["count"]
        .sum()
        .reset_index()
    )

    x = np.arange(len(query_types))

    group_width = 0.6
    max_bar_width = 0.25
    bar_width = min(max_bar_width, group_width / len(runs))
    offset = (len(runs) - 1) * bar_width / 2

    plt.figure(figsize=(10, 6))

    for i, run in enumerate(runs):
        values = [
            counts[(counts["type"] == t) & (counts["run"] == run)]["count"].values[0]
            if not counts[(counts["type"] == t) & (counts["run"] == run)].empty else 0
            for t in query_types
        ]

        plt.bar(
            x + i * bar_width - offset,
            values,
            width=bar_width,
            color=run_colors[run],
            label=f"{run}"
        )

    plt.xticks(x, query_types, fontsize=8)
    plt.xlabel("Query type", fontsize=9)
    plt.ylabel("Number of failed queries", fontsize=9)
    plt.legend(fontsize="large", fancybox=True, framealpha=0.5)
    plt.tight_layout()

    save(file_path, metrics_folder, suffix)

def plot_query_box_latency(latency_df, query_types, runs, run_colors, file_path, suffix):
    positions, box_data, colors = [], [], []
    pos, gap = 0, 0.5

    for t in query_types:
        for run in runs:
            values = latency_df[
                (latency_df["type"] == t) &
                (latency_df["run"] == run)
            ]["latency"].values

            if len(values) == 0:
                continue

            box_data.append(values)
            positions.append(pos)
            colors.append(run_colors[run])
            pos += 1

        pos += gap

    plt.figure(figsize=(10, 6))
    bp = plt.boxplot(box_data, positions=positions, widths=0.6,
                     patch_artist=True, showfliers=False)

    for patch, color in zip(bp["boxes"], colors):
        patch.set_facecolor(color)

    for median in bp["medians"]:
        median.set_color("black")

    centers, start = [], 0
    for _ in query_types:
        centers.append(start + (len(runs) - 1) / 2)
        start += len(runs) + gap
        
    xlim = plt.xlim()
    xlim_range = xlim[1] - xlim[0]
    mid = (xlim[1] + xlim[0]) / 2
    if 0.6 > 0.2 * xlim_range:
        plt.xlim((mid - xlim_range, mid + xlim_range))

    plt.xticks(centers, query_types, fontsize=8)
    plt.xlabel("Query type", fontsize=9)
    plt.ylabel("Latency (ms)", fontsize=9)

    handles = [plt.Line2D([0], [0], color=run_colors[r], lw=4) for r in runs]
    plt.legend(handles, runs, fontsize="large", fancybox=True, framealpha=0.5)

    plt.tight_layout()
    save(file_path, metrics_folder, suffix)
    
def plot_query_box_volume(volume_df, query_types, runs, run_colors, file_path, suffix):
    positions, box_data, colors = [], [], []
    pos, gap = 0, 1

    for t in query_types:
        for run in runs:
            values = volume_df[
                (volume_df["type"] == t) &
                (volume_df["run"] == run)
            ]["volume"].values
            
            if len(values) == 0:
                continue

            box_data.append(values)
            positions.append(pos)
            colors.append(run_colors[run])
            pos += 1

        pos += gap

    plt.figure(figsize=(10, 6))
    bp = plt.boxplot(box_data, positions=positions, widths=0.6,
                     patch_artist=True, showfliers=False)

    for patch, color in zip(bp["boxes"], colors):
        patch.set_facecolor(color)

    for median in bp["medians"]:
        median.set_color("black")

    centers, start = [], 0
    for _ in query_types:
        centers.append(start + (len(runs) - 1) / 2)
        start += len(runs) + gap
        
    xlim = plt.xlim()
    xlim_range = xlim[1] - xlim[0]
    mid = (xlim[1] + xlim[0]) / 2
    if 0.6 > 0.2 * xlim_range:
        plt.xlim((mid - xlim_range, mid + xlim_range))

    plt.xticks(centers, query_types, fontsize=8)
    plt.xlabel("Query type", fontsize=9)
    plt.ylabel("Volume (records/query)", fontsize=9)

    handles = [plt.Line2D([0], [0], color=run_colors[r], lw=4) for r in runs]
    plt.legend(handles, runs, fontsize="large", fancybox=True, framealpha=0.5)

    plt.tight_layout()
    save(file_path, metrics_folder, suffix)
    
def compute_avg_latency_per_run(latency_df, interval="15s"):
    avg_records = []

    for run in latency_df["run"].unique():
        run_df = latency_df[latency_df["run"] == run].copy()

        # Convert seconds back to TimedeltaIndex
        run_df["time_td"] = pd.to_timedelta(run_df["time"], unit="s")
        run_df = run_df.set_index("time_td")

        avg = (
            run_df["latency"]
            .resample(interval)
            .mean()
            .dropna()
        )

        avg_records.append(pd.DataFrame({
            "run": run,
            "time": seconds_millis(avg.index),
            "latency": avg.values
        }))

    return pd.concat(avg_records, ignore_index=True)
    
def plot_avg_latency_per_run(avg_latency_df, run_colors, file_path, suffix):
        
    fig, ax = plt.subplots(figsize=(8, 5))

    for run, color in run_colors.items():
        run_df = avg_latency_df[avg_latency_df["run"] == run]
        if run_df.empty:
            continue

        ax.plot(
            run_df["time"],
            run_df["latency"],
            label=f"{run}",
            color=color,
            linewidth=1.2,
            alpha=0.9
        )

    ax.set_xlabel("Elapsed time (s)")
    ax.set_ylabel("Average query latency (ms)")
    ax.set_xlim(left=0)
    ax.set_ylim(top=ax.get_ylim()[1] * 1.5)
    ax.legend(fontsize="large", fancybox=True, framealpha=0.5)

    plt.tight_layout()
    save(file_path, metrics_folder, suffix)

def plot_query_client(file_path: str, query_groups_per_run):

    query_types = ["AGGREGATION", "DOWNSAMPLING", "OUTLIER_FILTER"]

    latency_df = collect_latency_records(query_groups_per_run, query_types)
    volume_df = collect_volume_records(query_groups_per_run, query_types)

    if latency_df.empty:
        return

    runs = sorted(latency_df["run"].unique(), key=lambda x: (x.lower() not in ["control", "reference"], x.lower()))

    cmap = plt.get_cmap("tab10")
    run_colors = {
        run: (*cmap(i % 10)[:3], cmap(i % 10)[3] * 0.85)
        for i, run in enumerate(runs)
    }

    # ---- Per-query-type plots ----
    for t in query_types:
        if t not in latency_df["type"].unique():
            continue

        subset = latency_df[latency_df["type"] == t]
        subset_v = volume_df[volume_df["type"] == t]

        plot_query_line(subset, t, run_colors, file_path, f"-{t.lower()}-query-latency")
        plot_query_bar_latency(subset, [t], runs, run_colors, file_path, f"-{t.lower()}-bar")
        plot_query_bar_volume(subset_v, [t], runs, run_colors, file_path, f"-{t.lower()}-volume-bar")
        plot_query_box_latency(subset, [t], runs, run_colors, file_path, f"-{t.lower()}-box")

    plot_query_bar_latency(latency_df, query_types, runs, run_colors, file_path, "-query-latency-bar")
    plot_query_bar_volume(volume_df, query_types, runs, run_colors, file_path, "-query-volume-bar")
    plot_query_box_latency(latency_df, query_types, runs, run_colors, file_path, "-query-latency-box")
    # plot_query_box_latency(latency_df[latency_df["type"] != "OUTLIER_FILTER"], ["AGGREGATION", "DOWNSAMPLING"], runs, run_colors, file_path, "-query-latency-box")
    plot_query_box_volume(volume_df, query_types, runs, run_colors, file_path, "-query-volume-box")
    
    runs_2 = sorted(query_groups_per_run.keys())

    failed_df = collect_failed_query_counts(query_groups_per_run, query_types)

    plot_failed_queries_bar(failed_df, query_types, runs_2, run_colors, file_path, "-query-failed-bar")

    avg_latency_df = compute_avg_latency_per_run(latency_df)

    plot_avg_latency_per_run(
        avg_latency_df,
        run_colors,
        file_path,
        "-query-latency"
    )

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

            client_df.sort_values('after', inplace=True)
            client_df.set_index('after', inplace=True)

            
            aux.append(client_df)

        client_df_per_run[run] = pd.concat(aux)
        client_df_per_run[run].sort_values('after', inplace=True)
        query_groups[run] = client_df_per_run[run].groupby('type')          
        
    # sort the client dfs so that the default one is first
    query_groups = dict(sorted(query_groups.items(), key=lambda x: (x[0].lower() not in ["control", "reference"], x[0].lower())))

    if any("INSERT" in query_groups[run].groups.keys() for run in query_groups):
        pass
    else:
        plot_query_client(file_path, query_groups)

def plot_aggregate_benchmark_clients(file_paths):
    clients = {}
    
    for sub_file_paths in file_paths:
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
        insert_clients_dfs_transformed = dict(sorted(insert_clients_dfs_transformed.items(), key=lambda x: (x[0].lower() not in ["control", "reference"], x[0].lower())))

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

    try:
        font_manager.fontManager.addfont('NewsGotT.ttf')
    except Exception:
        pass

    compute_stage_statistics()
    
    print(stage_stats)
    
    if PLOT_INDIVIDUAL_CLIENTS:
        
        print("Plotting individual clients")
        
        clients = set()
        
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
                        clients.add(file_path)
        
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
