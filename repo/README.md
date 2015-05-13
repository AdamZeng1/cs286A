#Metadata Repository

A git-like repository specialized for metadata that allows users to query metadata, including past metadata.

###Installation

1. **Install** and **run** MongoDB on the machine that will hold the repository. The instruction can be found [here](http://docs.mongodb.org/manual/installation/).

2. Clone this repo.

3. Go to MetadataRepo project directory.
```
cd cs286A/repo/MetadataRepo
```

3. Edit `src/main/java/edu/berkeley/MetadataRepo/Main.java`. Replace the IP address in the line shown below (currently set to our EC2 IP) with the IP address of your machine that runs the MongoDB server.

```
MetadataRepo repo = new MetadataRepo("54.69.1.154"); // change this IP
```

4. Compile the project.
```
cd cs286A/repo/MetadataRepo/
mvn compile
```

5. Package it into a jar.
```
mvn package
```

6. Run the Metadata Repo shell.
```
java -cp target/MetadataRepo-jar-with-dependencies.jar edu.berkeley.MetadataRepo.Main
```

7. Now you can try all of the commands in our demo to learn our features! See the demo [here](https://docs.google.com/document/d/11U0WeBf4OqFKGgsRTBcfiz9UUfmR34J0-tdp5sg-6K8/edit#heading=h.9ka0j4izoy1g).