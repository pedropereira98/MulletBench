# Configuration options

The configuration file is an Ansible inventory file, meaning it must follow its structure conventions. You can read more about how these files are structured in [the official Ansible documentation](https://docs.ansible.com/ansible/latest/inventory_guide/intro_inventory.html). By defining specific variables for hosts in specific groups, one can create a custom test configuration.

Hosts defined in the *edgeservers* group refer to the edge database nodes to be used in a test's execution. Similarly, hosts defined in the *cloudservers* group refer to cloud database nodes. Both of these groups differ only in the ability for hosts in the *edgeservers* group to be configured for resource limiting and replication. All other options are shared between both groups.

## Database options

### Common options (apply to edge or cloud nodes)

The following options should be set for hosts in the *edgeservers* or *cloudservers* groups

| Option | Description |
| --- | --- |
| database  | name of tested database (influx or iotdb) |
| downsampling | whether to perform downsampling on inserted data, boolean|
| downsampling\_interval | time interval to use for aggregation (e.g., 1s)|
| network\_sim | list of entries for network simulation configurations |
| blkio\_path | block device path for IO limiting|

#### Network simulation options

| Option | Description |
| --- | --- |
| target |  name of node to target in network configuration |
| latency |  amount for roundtrip network delay (e.g., 50ms) |
| latency\_normal\_distribution |  variation to be applied over latency with normal distribution (e.g., 10ms) |
| bandwidth |  network bandwidth rate (e.g., 400Kbps) |
| reordering\_rate |  percentage of packet reordering rate |


### Edge node options

| Option | Description |
| --- | --- |
| limited\_resources | whether node container resources should be limited, boolean|
| use\_resource\_profile | whether to use preset resource profile for limiting, boolean|
| limited\_profile | name of preset resource profile|
| limited\_resources\_cpu | limit for CPU usage in cores |
| limited\_resources\_mem | limit for memory usage in bytes (e.g., 2048M) |
| limited\_resources\_read\_bps | limit for read bytes per second in bytes (e.g., 15M) |
| limited\_resources\_write\_bps | limit for written bytes per second in bytes |
| limited\_resources\_read\_iops | limit for read operations per second in operations |
| limited\_resources\_write\_iops | limit for write operations per second in operations |
replication\_targets | list of cloud nodes to replicate data to|

Some variables are needed for each supported database:

#### InfluxDB options

| Option | Description |
| --- | --- |
| influx\_username | username for super-user |
| influx\_password | password for super-user |
| influx\_org | name of organization |
| influx\_bucket | bucket name for data |
| influx\_token | authentication token  |
| influx\_version | version of InfluxBD to use |
| influx\_port | specific port for InfluxBD instance (defaults to 8086)|

#### IoTDB options

| Option | Description |
| --- | --- |
| iotdb\_device\_path | path for devices in iotdb (e.g. "root.gps.mpu.*" )|
| iotdb\_version | version of Apache IoTDB to use |
| iotdb\_port | specific port for IoTDB instance (defaults to 6667)|

### Cloud node options

There are no specific options for cloud nodes.

## Orchestrator options

Orchestrator options should be set for the localhost, as the orchestrator is run locally.

| Option | Description |
| --- | --- |
| local | whether to locally build the Orchestrator image, boolean (defaults to false) |
| database  | name of tested database (to know name of container to monitor with PCP) |
| monitoring\_interval  | string to define at which rate monitoring should be performed (as received by PCP)  |
| final\_monitoring\_period\_sec | length of additional database resource monitoring after test is finished in seconds |

## Client options

Client options should be set for hosts in the *benchmark_clients* group. 

| Option | Description |
| --- | --- |
| local | whether to locally build the Client image, boolean (defaults to false) |
| dataset  | name of dataset used in test  |
| current\_time | whether operations should use current time or original dataset time, boolean|
| shared\_dataset | whether the loaded dataset should be shared by all workers, boolean |
| shared\_connection | whether database connection should be shared by all workers, boolean |
| stage | stage to which Client belongs, integer |
| monitor | whether Client container should be monitored, boolean |
| target | name of host targeted by Client |
| data\_file | name of data file to use |
| num\_workers | number of workers to generate load |
| type | type of load to be generated, insert or query |
| insert\_volume | total number of entries to be inserted by each worker|
| batch\_size | number of entries in each insert operation|
| insert\_rate | number of insert operations per second per worker |
| query\_count | total number of query operations to be submitted |
| query\_rate | number of query operations per second per worker |
| agg\_weight | weight for probability of aggregation queries |
| filter\_weight | weight for probability of filter queries |
| downsample\_weight | weight for probability of downsampling queries |
| outlier\_filter\_weight | weight for probability of outlier filter queries |
| count\_outlier\_filter | whether outlier filter queries should use count aggregation, boolean  |
| filter\_z\_score | target z-score used for outlier filters |
| fjp\_parallelism | pending request ForkJoinPool size |
| max\_memory | maximum to be used by Client container (e.g. 4G) |
| orchestrator\_ip | IP address of orchestrator node|
| write\_timeout | database write operation timeout in seconds|
| read\_timeout | database read operation timeout in seconds|

## Example configurations

Some example configurations are provided in the [examples](examples) folder.