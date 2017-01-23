##Testing with SCDF

To generate the SCDF deployable artifact:
 
 1  ./mvnw clean install -PgenerateApps
 
 2.  cd apps/pgcopy-sink-rabbit
 
 3.  ./mvnw package
 
 4.  upload resulting jar file to your maven or http accessible location.  Jar file target/pgcopy-sink-rabbit-1.1.0.BUILD-SNAPSHOT.jar
