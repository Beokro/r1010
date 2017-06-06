# Start Remote Bloomfilter on Eucalyptus/EC2
    $ mvn package
    $ nohup 2>&1 ./start_filter.sh &
# Algorithm Usage

    $ mvn package
    $ nohup 2>&1 java -jar target/RoyCrab-2.0-jar-with-dependencies.jar <server ip> <Remote Bloomfilter ip> &
