# Adding a database

In order to add support for benchmarking a database system, several files will need to be added, both to the Ansible and Java components of MulletBench.

## Ansible support

All files to add will be located in the MulletBench/ansible/roles directory. The paths indicated are relative to that directory. 
Files to add:

- common-database/tasks/\<database>-main.yaml
  - This file should handle configurations tha apply to all database nodes, such as defining the default port, making sure database's folders exist and running the database container. The compose file used for the container should be in common-database/templates.
- common-database/tasks/\<database>-shutdown.yaml
  - The file should handle stopping the database's container and deleting its data when necessary.
- clouds/tasks/\<database>-main.yaml
  - This file should handle the configuration of cloud nodes in order to support replication and downsampling. This may include setting facts for other hosts to access. This file can also make use of templates in the clouds/templates directory.
- edges/tasks/\<database>-main.yaml
  - This file should handle any configuration that applies to edge database nodes and call *include_facts* for the following two files.
- edges/tasks/\<database>-replication.yaml
  - This file should handle setting up replication to the cloud nodes for each cloud node listed in the *replication_targets* variable.
- edges/tasks/\<database>-stop-replication.yaml
  - This file should handle making sure the database instance is no longer replicating data to other database instance.

Files to edit:

- benchmark-client/templates/config.yml.j2
  - Should add the variables reflecting the \<database>Options java class.

## Java support

Files to add:
- mulletbench/mulletbench-client/src/main/java/pt/haslab/mulletbench/database/\<database>/\<Database>Connector.java
  - Implement the DatabaseConnector interface, enabling write and query operations.
- mulletbench/mulletbench-client/src/main/java/pt/haslab/mulletbench/queries/queryBuilders/\<Database>QueryBuilder.java
  - Extend the QueryBuilder class, implementing the logic for building queries compatible with the target database.
- mulletbench/mulletbench-client/src/main/java/pt/haslab/mulletbench/utils/\<Database>Options.java
  - Class defining options specific to the database usage

Files to edit:

- mulletbench/mulletbench-client/src/main/java/pt/haslab/mulletbench/database/DatabaseConnectorFactory.java
  - Add a switch case option returning the database's Connector implementation
- mulletbench-client/src/main/java/pt/haslab/mulletbench/queries/queryBuilders/QueryBuilder.java
  - Add a switch case option returning the database's QueryBuilder implementation.
- mulletbench/mulletbench-client/src/main/java/pt/haslab/mulletbench/queries/queryGenerators/QueryGenerator.java
  - Add a switch case option for the target database's option corresponding to identifying the target data table/bucket or equivalent.
- mulletbench/mulletbench-client/src/main/java/pt/haslab/mulletbench/utils/ClientOptions.java
  - Add an attribute for the database's specific options with the respective \<Database>Options class.