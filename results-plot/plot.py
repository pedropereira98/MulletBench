import datetime
import pandas as pd
import matplotlib.pyplot as plt
from pathlib import Path
import re
import os
import glob
from datetime import datetime, timedelta

epoch = datetime.utcfromtimestamp(0)
images_folder = "images/"
data_folder = "data/"
metrics_folder = "metrics/"
monitoring_folder = "monitoring/"
os.makedirs(images_folder, exist_ok=True)
os.makedirs(images_folder+metrics_folder, exist_ok=True)
os.makedirs(images_folder+monitoring_folder, exist_ok=True)

DATE_FORMAT = "%Y-%m-%d %H:%M:%S.%f"
# FORMAT="pdf"
FORMAT="png"

# CUT = True
CUT = False
axis1_min = 0
axis1_max = 3000
axis2_min = 15000
axis2_max = 18000

stages_min = [] #minimum timestamp for each stage
stages_max = [] #max timestamp for each stage
current_stage = 0 #works if 

PLOT_INDIVIDUAL_CLIENTS = True
PLOT_AGGREGATE_CLIENTS = True
PLOT_CLIENT_MONITORING = False
PLOT_DB_MONITORING = False

plt.rcParams['font.family'] = ['NewsGotT'] #font for thesis
max_test_timestamp = 0

def unix_time_millis(dt):
    return (dt - epoch).total_seconds() * 1000.0

def set_axis():
    ax = plt.gca()
    if not CUT:
        ax.set_ylim(bottom=0)
        ax.set_xlim(left=0)

def break_plot(ax, ax2):
    ax.set_ylim(axis2_min, axis2_max)  # most of the data
    ax2.set_ylim(axis1_min, axis1_max)  # outliers only

    ax.set_xlim(left=0)
    ax2.set_xlim(left=0)

    # hide the spines between ax and ax2
    ax.spines['bottom'].set_visible(False)
    ax2.spines['top'].set_visible(False)
    # ax.xaxis.tick_top()
    ax.xaxis.set_tick_params(length=0)
    ax.tick_params(labeltop=False)  # don't put tick labels at the top
    ax2.xaxis.tick_bottom()

    d = .005  # how big to make the diagonal lines in axes coordinates
    # arguments to pass to plot, just so we don't keep repeating them
    kwargs = dict(transform=ax.transAxes, color='k', clip_on=False)
    ax.plot((-d, +d), (-d, +d), **kwargs)        # top-left diagonal
    ax.plot((1 - d, 1 + d), (-d, +d), **kwargs)  # top-right diagonal

    kwargs.update(transform=ax2.transAxes)  # switch to the bottom axes
    ax2.plot((-d, +d), (1 - d, 1 + d), **kwargs)  # bottom-left diagonal
    ax2.plot((1 - d, 1 + d), (1 - d, 1 + d), **kwargs)  # bottom-right diagonal

def show():
    set_axis()
    plt.show(block=True)
    plt.clf()

def save(file_path: str, subfolder:str, append: str = ""):
    filename = Path(file_path).stem + append + "." + FORMAT
    set_axis()
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

# converts timedeltaindex to seconds float with milliseconds
def seconds_millis(timedelta: pd.TimedeltaIndex):
    return timedelta.seconds + timedelta.microseconds / 1_000_000

def parse_monitor_datetime(datetime_str: str):
    return datetime.strptime(datetime_str, DATE_FORMAT)

def monitor_df_from_path(file_path: str):
    global current_stage, stages_max, stages_min

    #reading data with format time (datetime), lo-in (float), eth0-in (float), lo-out (float),eth0-out (float), RAM (float), cpu-system (float), cpu-user (float), io-read (float), io-write (float)
    monitor_df = pd.read_csv(file_path)

    interface_columns = get_interface_columns(monitor_df)

    monitor_df.columns = ['time', interface_columns[0], interface_columns[1], interface_columns[2], interface_columns[3], 'RAM', 'cpu-system', 'cpu-user', 'io-read', 'io-write']


    if "client" in file_path:
        time_min = parse_monitor_datetime(monitor_df['time'].min())
        time_max = parse_monitor_datetime(monitor_df['time'].max())

        # populate for first df
        if len(stages_max) == 0 :
            stages_min.append(time_min)
            stages_max.append(time_max)

        found_stage = False
        for stage in range(current_stage + 1):
            # within 5 seconds of the current stage min_time -> same stage
            if time_min < stages_min[stage] + timedelta(seconds=5) and time_min > stages_min[stage] - timedelta(seconds=5):
                stages_max[stage] = max(stages_max[stage],time_max)
                found_stage = True
        if not found_stage:
            current_stage = current_stage + 1
            stages_min.append(time_min)
            stages_max.append(time_max)

    # change time to time elapsed
    monitor_df['time'] = pd.to_datetime(monitor_df['time'])
    minutes_time = monitor_df['time'].min()
    monitor_df['time'] =  seconds_millis((monitor_df['time'] - minutes_time).dt)

    return monitor_df

def plot_stage_lines(max_relative_timestamps):
    for i,stage_ts in enumerate(max_relative_timestamps):
        plt.axvline(x=stage_ts, c='r')
        if i == len(max_relative_timestamps)-1:
            plt.text(stage_ts,1.02,'Test finish',ha='center',rotation=0, transform=plt.gca().get_xaxis_transform())

        if i == 0:
            text = "Pre-population"
        if i == 1:
            text = "Mixed stage"

        # text = f'Stage {i+1}'
        if i == 0:
            prev_stage = 0
        else:
            prev_stage = max_relative_timestamps[i-1]
        
        stage_label_x = (stage_ts + prev_stage)/2
        print(f'stage_ts {stage_ts} prev_stage {prev_stage} stage_label_x { stage_label_x}')

        plt.text(stage_label_x,1.02,text,ha='center',rotation=0, transform=plt.gca().get_xaxis_transform())
        print(text)



def plot_monitoring(file_path: str):
    monitor_df = monitor_df_from_path(file_path)

    max_relative_timestamps = []
    if not "client" in file_path:
        min_abs_timestamp = min(i for i in stages_min)
        for max_abs_timestamp in stages_max:
            relative_timestamp = max_abs_timestamp - min_abs_timestamp
            max_relative_timestamps.append(relative_timestamp.seconds + relative_timestamp.microseconds / 1_000_000)

        max_relative_timestamps = sorted(max_relative_timestamps,key=float)
        print(max_relative_timestamps)
    
    if "client" in file_path and not PLOT_CLIENT_MONITORING:
        return

    #plot line graph with cpu-system and cpu-user for time with y start from 0
    monitor_df['cpu-total'] = monitor_df['cpu-system'] + monitor_df['cpu-user']
    monitor_df.plot(x='time', y= ['cpu-total'], )
    plt.fill_between(monitor_df['time'], monitor_df['cpu-total'], color='skyblue', alpha=0.4)
    # plt.title("Total CPU usage")
    plt.ylabel("% up to 100 * number of cores")
    plt.xlabel("Elapsed time (s)")

    plot_stage_lines(max_relative_timestamps)

    set_axis()

    save(file_path, monitoring_folder, "-cpu")

    #plot line graph with RAM for time
    monitor_df.plot(x='time', y= ['RAM'])
    plt.fill_between(monitor_df['time'], monitor_df['RAM'], color='skyblue', alpha=0.4)
    # plt.title("Memory Usage")
    plt.ylabel("MB")
    plt.xlabel("Elapsed time (s)")

    plot_stage_lines(max_relative_timestamps)

    set_axis()

    save(file_path, monitoring_folder, "-memory")

    #plot line graph with eth0-in and eth0-out for time

    monitor_df.plot(x='time', y= ['eth0-in', 'eth0-out'])
    plt.fill_between(monitor_df['time'], monitor_df['eth0-in'], color='skyblue', alpha=0.4)
    plt.fill_between(monitor_df['time'], monitor_df['eth0-out'], color='orange', alpha=0.4)
    # plt.title("eth0 interface usage")
    plt.ylabel("Throughput (MB/s)")
    plt.xlabel("Elapsed time (s)")

    plot_stage_lines(max_relative_timestamps)

    set_axis()

    save(file_path, monitoring_folder, "-network")

    #plot line graph with io-read and io-write for time
    monitor_df.plot(x='time', y= ['io-read', 'io-write'])
    plt.fill_between(monitor_df['time'], monitor_df['io-read'], color='skyblue', alpha=0.4)
    plt.fill_between(monitor_df['time'], monitor_df['io-write'], color='orange', alpha=0.4)
    # plt.title("Disk I/O")
    plt.ylabel("Throughput (MB/s)")
    plt.xlabel("Elapsed time (s)")

    plot_stage_lines(max_relative_timestamps)
    
    set_axis()

    save(file_path, monitoring_folder, "-io")

def plot_insert_client(file_path: str, client_df, query_groups, coarse_aggregation: bool = False):
    # plot scatter latency for inserts
    # print(client_df.index)

    query_groups = client_df.groupby('type')
    fig, ax = plt.subplots()
    for name, group in query_groups:
        if 'FAILED' in name:
            color = '#d62728'
        else:
            color = None
        sc = ax.scatter(seconds_millis(group.index), group.latency,c=color, label=name, alpha = 0.8, s=4)
        ax.legend(*sc.legend_elements()[0],bbox_to_anchor=(0, 1.02, 1, 0.2), loc="lower left",
                mode="expand", borderaxespad=0, ncol=3)

    # plt.title("Insert latency")
    plt.ylabel("Latency (ms)")
    plt.xlabel("Elapsed time (s)")

    save(file_path, metrics_folder, "-insert-latency")

    # plot throughput for inserts

    if coarse_aggregation:
        insert_latency =  query_groups.get_group("INSERT")['latency']
        latency_mean = insert_latency.resample("10s").mean()

        latency_mean.index = latency_mean.index.map(lambda el : seconds_millis(el))

        latency_mean.plot()
        # plt.title("Average insert latency")
        plt.ylabel("Latency (ms)")
        plt.xlabel("Elapsed time (s)")

        save(file_path, metrics_folder, "-insert-latency-trend")


    insert_amount =  query_groups.get_group("INSERT")['amount']
    if coarse_aggregation:
        insert_throughput = insert_amount.resample("10s").sum()
        insert_throughput = insert_throughput/10


        insert_throughput.index = insert_throughput.index.map(lambda el : seconds_millis(el))

        insert_throughput.plot()

        # plt.title("Insertion throughput")
        plt.ylabel("Throughput (lines/s)")
        plt.xlabel("Elapsed time (s)")

        save(file_path, metrics_folder, "-throughput-downsampled")

    insert_throughput = insert_amount.resample("1s").sum()

    insert_throughput.index = insert_throughput.index.map(lambda el : seconds_millis(el))

    insert_throughput.plot()

    # plt.title("Insertion throughput")
    plt.ylabel("Throughput (lines/s)")
    plt.xlabel("Elapsed time (s)")

    save(file_path, metrics_folder, "-throughput")

def plot_query_client(file_path: str, client_df, query_groups):
    query_groups = client_df.groupby('type')

    print(query_groups)

    # query_groups = query_groups.sorted(key= lambda x :  )

    # plot scatter latency per query type
    if CUT:
        figure, (ax, ax2) = plt.subplots(2, 1, sharex=True)
    else: 
        figure, ax = plt.subplots()
    for name, group in query_groups:
        if 'FAILED' in name:
            color = '#d62728'
        else:
            color = None
        sc = ax.scatter(seconds_millis(group.index), group.latency,c=color, label=name, alpha = 0.8, s=4)
        ax.legend(*sc.legend_elements()[0], title="Query Type", bbox_to_anchor=(0, 1.02, 1, 0.2), loc="lower left",
                mode="expand", borderaxespad=0, ncol=3)
        if CUT:
            sc = ax2.scatter(seconds_millis(group.index), group.latency, label=name, alpha = 0.8, s=4)



    if CUT:
        break_plot(ax, ax2)

    # plt.title("Latency per Query Types")
    # plt.ylabel("Latency (ms)")
    figure.text(0.02, 0.5, r"Latency (ms)", va="center", rotation="vertical")

    plt.xlabel("Elapsed time (s)")
    plt.yscale('log')
    save(file_path, metrics_folder, "-query-latency")


def plot_benchmark_client(file_path: str):

    ## reading data with format before (timetamp), after (timestamp), amount (int), type (string)
    client_df  = pd.read_csv(file_path)

    client_df.columns = ['before', 'after', 'amount', 'type']

    client_df['latency'] = (client_df['after'] - client_df['before']) / 1_000_000

    client_df['after'] = pd.to_datetime(client_df['after'], unit='ns')
    min_after = client_df['after'].min()
    client_df['after'] =  (client_df['after'] - min_after)


    client_df.sort_values('after', inplace=True)
    client_df.set_index('after', inplace=True)


    query_groups = client_df.groupby('type')
    if "INSERT" in query_groups.groups.keys():
        # plot_insert_client(file_path, client_df, query_groups)
        pass
    else:
        plot_query_client(file_path, client_df, query_groups)

def plot_aggregate_benchmark_clients(file_paths: list):
    insert_client_dfs = []

    min_after = pd.Timestamp.max

    for file_path in file_paths:

        client_df  = pd.read_csv(file_path)

        client_df.columns = ['before', 'after', 'amount', 'type']
        query_groups = client_df.groupby('type')
        if "INSERT" in query_groups.groups.keys():
            client_df['after'] = pd.to_datetime(client_df['after'], unit='ns')
            min_after = min(client_df['after'].min(),min_after)


    for file_path in file_paths:
        ## reading data with format before (timetamp), after (timestamp), amount (int), type (string)
        client_df  = pd.read_csv(file_path)

        client_df.columns = ['before', 'after', 'amount', 'type']
        query_groups = client_df.groupby('type')
        if "INSERT" in query_groups.groups.keys():

            client_df['latency'] = (client_df['after'] - client_df['before']) / 1_000_000

            client_df['after'] = pd.to_datetime(client_df['after'], unit='ns')
            client_df['after'] =  (client_df['after'] - min_after)


            client_df.sort_values('after', inplace=True)
            client_df.set_index('after', inplace=True)

            insert_client_dfs.append(client_df)
            insert_amount =  query_groups.get_group("INSERT")['amount']
            insert_throughput = insert_amount.resample("10s").sum()

            insert_throughput = insert_throughput/10

            insert_throughput.index = insert_throughput.index.map(lambda el : seconds_millis(el))

            insert_throughput.plot()

    if len(insert_client_dfs) > 0:
        # plt.title("Insertion throughput")
        plt.ylabel("Throughput (lines/s)")
        plt.xlabel("Elapsed time (s)")

        save(data_folder+ "aggregate", metrics_folder, "-perclient-throughput")

        insert_clients_df = pd.concat(insert_client_dfs)

        insert_clients_df.sort_values('after', inplace=True)

        plot_insert_client(data_folder + "aggregate", insert_clients_df, insert_clients_df.groupby('type'), coarse_aggregation=True)
    

def main():

    if PLOT_INDIVIDUAL_CLIENTS:
        for client in glob.glob("client[0-9].csv", root_dir=data_folder) + glob.glob("client[0-9][0-9].csv", root_dir=data_folder):
            plot_benchmark_client(data_folder + client)

    if PLOT_AGGREGATE_CLIENTS:
        clients = []
        for client in glob.glob("client[0-9].csv", root_dir=data_folder) + glob.glob("client[0-9][0-9].csv", root_dir=data_folder):
            clients.append(data_folder + client)

        plot_aggregate_benchmark_clients(clients)

    if PLOT_CLIENT_MONITORING or PLOT_DB_MONITORING:
        for client_monitoring in glob.glob("monitor-client*.csv", root_dir=data_folder):
            plot_monitoring(data_folder + client_monitoring)

    if PLOT_DB_MONITORING:
        for db_monitoring in glob.glob("monitor-cloud*.csv", root_dir=data_folder) + glob.glob("monitor-edge*.csv", root_dir=data_folder):
            plot_monitoring(data_folder + db_monitoring)

if __name__ == "__main__":
    main()