SCE backend is the server for Smart Cloud/City Engine scheduling platform.

Prerequisites:

- Apache Tomcat7

- MySQL 5.6

Configuration:

- create 'quartz' database on mysql server
- create the tables using the script in the db directory
- change in dist/SmartCloudEngine.war file the WEB-INF/classes/sce/quartz.properties and update the database connection information
    org.quartz.dataSource.quartzDataSource.URL = jdbc:mysql://dbhost:3306/quartz
    org.quartz.dataSource.quartzDataSource.user = dbuser
    org.quartz.dataSource.quartzDataSource.password = dbpassword
- deploy on tomcat