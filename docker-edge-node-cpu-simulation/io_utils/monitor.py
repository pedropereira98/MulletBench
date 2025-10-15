import pandas as pd
import re

def monitor_df_from_path(file_path: str) -> pd.DataFrame:
    """ Load the monitor data from a CSV file and format the columns.

    Args:
        file_path (str): Path to the CSV file containing monitor data.

    Returns:
        pd.DataFrame: A DataFrame containing the formatted monitor data with appropriate column names.
    """

    def get_interface_columns(monitor_df):
        interface_re = re.compile(r"network\.interface\.(in|out)\.bytes-(.*)")
        interface_columns = []
        for column in range(0,4):
            matched = interface_re.match(monitor_df.columns[column+1])
            interface_columns.append(matched.group(2) + "-" + matched.group(1))
        
        return interface_columns

    monitor_df = pd.read_csv(file_path)

    interface_columns = get_interface_columns(monitor_df)

    monitor_df.columns = ['time', interface_columns[0], interface_columns[1], interface_columns[2], interface_columns[3], 'RAM', 'cpu-system', 'cpu-user', 'io-read', 'io-write']

    return monitor_df