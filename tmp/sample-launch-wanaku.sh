java -Dquarkus.http.host=0.0.0.0 -Dauth.server=http://$(hostname -f):8543 -jar target/quarkus-app/quarkus-run.jar
