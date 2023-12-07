# Vai buscar a pastas influx e iotdb (para saber que dados são de que bd)
import datetime
import os
import glob
import pandas as pd
import matplotlib.pyplot as plt

epoch = datetime.datetime.utcfromtimestamp(0)
IOTDB_DIR = "iotdb/"
INFLUX_DIR = "influx/"
DATA_DIR = "data/"
METRICS_DIR = "metrics/"
IOTDB_DATA_DIR = IOTDB_DIR + DATA_DIR
INFLUX_DATA_DIR = INFLUX_DIR + DATA_DIR
IOTDB_METRICS_DIR = IOTDB_DIR + METRICS_DIR
INFLUX_METRICS_DIR = INFLUX_DIR + METRICS_DIR

os.makedirs(INFLUX_DATA_DIR, exist_ok=True)
os.makedirs(INFLUX_METRICS_DIR, exist_ok=True)
os.makedirs(IOTDB_DATA_DIR, exist_ok=True)
os.makedirs(IOTDB_METRICS_DIR, exist_ok=True)

plt.rcParams['font.family'] = ['NewsGotT'] #font for thesis

DATABASES=[{
            'name':"InfluxDB",
            'data_dir':INFLUX_DATA_DIR,
            'metrics_dir':INFLUX_METRICS_DIR
            },{
            'name':"IoTDB",
            'data_dir':IOTDB_DATA_DIR,
            'metrics_dir':IOTDB_METRICS_DIR
            }]

CUT = False
axis1_min = 0
axis1_max = 100000
axis2_min = 300000
axis2_max = 400000

# stage_line = True
stage_line = False
stage_line_x = 1280 #default length for prepopulation

def unix_time_millis(dt):
    return (dt - epoch).total_seconds() * 1000.0

def set_axis():
    ax = plt.gca()
    ax.set_ylim(bottom=0)
    ax.set_xlim(left=0)


# converts timedeltaindex to seconds float with milliseconds
def seconds_millis(timedelta: pd.TimedeltaIndex):
    return timedelta.seconds + timedelta.microseconds / 1_000_000

def main():
    clients = []

    if CUT:
        _, (ax, ax2) = plt.subplots(2, 1, sharex=True)
    else:
        _, ax = plt.subplots()

    for database in DATABASES:
        clients = []
        print(database)


        for client in glob.glob("client[0-9].csv", root_dir=database['data_dir']) + glob.glob("client[0-9][0-9].csv", root_dir=database['data_dir']):
            clients.append(database['data_dir'] + client)

        insert_client_dfs = []

        min_after = pd.Timestamp.max

        # find the minimum "after" value for all clients (x_min)
        for file_path in clients:

            client_df  = pd.read_csv(file_path)

            client_df.columns = ['before', 'after', 'amount', 'type']
            query_groups = client_df.groupby('type')
            if "INSERT" in query_groups.groups.keys():
                client_df['after'] = pd.to_datetime(client_df['after'], unit='ns')
                min_after = min(client_df['after'].min(),min_after)


        for file_path in clients:
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


        insert_clients_df = pd.concat(insert_client_dfs)

        insert_clients_df.sort_values('after', inplace=True)


        insert_amount =  insert_clients_df.groupby('type').get_group("INSERT")['amount']
        
        insert_throughput = insert_amount.resample("10s").sum()
        insert_throughput = insert_throughput/10


        insert_throughput.index = insert_throughput.index.map(lambda el : seconds_millis(el))

        if CUT:
            ax.plot(insert_throughput)
            if database['name'] != "IoTDB":
                ax2.plot(insert_throughput)
        else:
            insert_throughput.plot()
        # ax.label(database['name'])
        # plt.legend(*pl.legend_elements()[0], title=database['name'])

    print([x for x in map(lambda db: db['name'],DATABASES)])
    ax.legend([x for x in map(lambda db: db['name'],DATABASES)])


    if CUT:
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

    if not CUT:
        set_axis()
    figure = plt.gcf()
    ax = plt.gca()

    if stage_line:
        # stage division line 
        plt.axvline(x=stage_line_x, c='r')
        
        # label for first stage
        preload_x = stage_line_x / 2
        plt.text(preload_x,1.02,'Pre-population',ha='center',rotation=0, transform=plt.gca().get_xaxis_transform())
        
        # label for second stage
        min_x, max_x = ax.get_xlim()
        print(f'x lim {max_x}')
        mixed_x = (stage_line_x + max_x) / 2 
        plt.text(mixed_x,1.02,'Mixed stage',ha='center',rotation=0, transform=plt.gca().get_xaxis_transform())



    # plt.title("Insertion throughput")
    # plt.ylabel("Throughput (lines/s)")
    figure.text(0.02, 0.5, r"Throughput (lines/s)", va="center", rotation="vertical")

    plt.xlabel("Elapsed time (s)")

    # figure.set_size_inches(6.5, 4.3)
    figure.set_size_inches(6.5, 3)
    plt.savefig("throughput-downsampled-compared.pdf", dpi=120, format="pdf", bbox_inches="tight")
    plt.clf()




if __name__ == "__main__":
    main()