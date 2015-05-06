package edu.berkeley.MetadataRepo;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;
import com.mongodb.MongoClient;
import com.mongodb.client.*;
import org.bson.Document;

import javax.print.Doc;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * A class that connects to a MongoDB database and handles all interactions with the database
 */
public class MetadataRepo
{
    private static final String TIMESTAMP = "__timestamp__";
    private static final long MILLISECOND_IN_A_DAY = 86400000;

    private MongoClient mongoClient;
    private MongoDatabase database;

    /**
     * Creates a connection to the metadata repository
     *
     * @param address the IP address of the metadata repository
     */
    public MetadataRepo(String address)
    {
        mongoClient = new MongoClient(address);
        database = mongoClient.getDatabase("MetadataRepo");
    }

    /**
     * Function that parses the user command and executes it accordingly
     *
     * @param command the command to be executed
     */
    public void execute(String command)
    {
        // No command entered
        if (command.length() == 0)
            return;

        String[] cmds = command.split(" ");
        String act =  cmds[0];

        try
        {
            // User can enter metadata into the database
            if (act.equals("commit"))
            {
                // Determine whether the user entered a timestamp or not
                Long timestamp = null;
                try {timestamp = Long.parseLong(cmds[3]);}
                catch (NumberFormatException e) {timestamp = null;}
                int i = timestamp == null? 3 : 4;

                // Ignore spaces for the metadata argument
                StringBuilder metadata = new StringBuilder();
                for (; i < cmds.length; i++)
                    metadata.append(cmds[i]);

                // Execute commit
                if (timestamp == null)
                    commit(cmds[1], cmds[2], metadata.toString());
                else
                    commit(cmds[1], cmds[2], metadata.toString(), timestamp);
            }
            // User can view all metadata in the database
            else if (act.equals("dump"))
            {
                dump();
            }
            // User can view a particular file from the database
            else if (act.equals("show"))
            {
                // Parameters for 'show':
                // cmd[1]: String namespace
                // cmd[2]: String file
                if (cmds.length == 4)
                   show(cmds[1], cmds[2], cmds[3]);
                else
                    show(cmds[1], cmds[2]);

            }
            // User can view all files with a particular key-value pair from the database
            else if (act.equals("find"))
            {
                // Parameters for 'find':
                // cmd[1]: String namespace
                // cmd[2]: String keyword  (format: "key"="value")
                // cmd[3]: String time
                if (cmds.length == 4)
                    find(cmds[1], cmds[2], cmds[3]);
                else
                    find(cmds[1], cmds[2]);
            }
            // User can delete a namespace from the database
            else if (act.equals("clear"))
            {
                // Parameters for 'clear':
                // cmd[1]: String namespace
                clear(cmds[1]);
            }
            // User does not input a valid command
            else
            {
                // Print error message
                System.out.println("Error: Unrecognized command");
            }
        }
        catch (Exception e)
        {
            // Print error message
            System.out.println("Error: Syntax error in command");
            System.out.println(e.toString());
        }
    }

    /**
     * This function allows the user to enter metadata into the database
     *
     * @param namespace
     * @param file
     * @param jsonMetadata
     */
    public void commit(String namespace, String file, String jsonMetadata)
    {
        commit(namespace, file, jsonMetadata, System.currentTimeMillis());
    }

    /**
     * This function allows the user to enter metadata into the database
     *
     * @param namespace
     * @param file
     * @param jsonMetadata
     * @param timestamp
     */
    public void commit(String namespace, String file, String jsonMetadata, long timestamp)
    {
        MongoCollection<Document> collection = database.getCollection(namespace);

        // Find a document with the given name
        Document fdoc = new Document("file", file);
        FindIterable<Document> found = collection.find(fdoc);

        if (found.iterator().hasNext())
        {
            // If a document is found, it should be the only one
            Document doc = found.iterator().next();
            ArrayList<Document> metadataList  = (ArrayList<Document>) doc.get("metadata");
            Document metadata = Document.parse(jsonMetadata);
            // Add a timestamp to this commit
            metadata.append(TIMESTAMP, new Date(timestamp));

            // Update the metadata document in the database
            metadataList.add(metadata);
            collection.updateOne(fdoc, new Document("$set", new Document("metadata", metadataList)));
        }
        else
        {
            // Document not found; create a new document to enter into the specified namespace
            Document doc = new Document();
            doc.append("file", file);
            ArrayList<Document> metadataList = new ArrayList<Document>();
            Document metadata = Document.parse(jsonMetadata);
            // Add a timestamp to this commit
            metadata.append(TIMESTAMP, new Date(timestamp));

            // Insert the new metadata document to the database
            metadataList.add(metadata);
            doc.append("metadata", metadataList);
            collection.insertOne(doc);
        }

        // Print confirmation message
        System.out.println("Committed '" + file + "' to namespace '" + namespace + "'");
    }

    /**
     * This function allows the user to view all metadata in the database
     */
    public void dump()
    {
        // Loop through all namespaces
        for (String namespace : database.listCollectionNames()) {
            if (namespace.equals("system.indexes"))
                continue;

            // Retrieve the collection of documents in the current namespace
            MongoCollection<Document> collection = database.getCollection(namespace);

            System.out.println("=======================================================================");
            System.out.println("Namespace: " + namespace);
            System.out.println("-----------------------------------------------------------------------");
            // Print all documents
            for (Document d : collection.find())
                System.out.println(d.toJson());
            System.out.println("=======================================================================");
        }
    }

    /**
     *
     *
     * @param namespace
     * @param file
     */
    public void show(String namespace, String file)
    {
        show(namespace, file, null);
    }

    /**
     * This function allows the user to view a particular file from the database
     *
     * @param namespace
     * @param file
     * @param time
     */
    public void show(String namespace, String file, String time)
    {
        // Retrieve the collection of documents in the specified namespace
        MongoCollection<Document> collection = database.getCollection(namespace);

        // A list to hold the pipeline steps for MongoDB's aggregate
        List<BasicDBObject> pipeline = new ArrayList<BasicDBObject>();

        // First, unwind the metadata array. See MongoDB documentation to understand what unwind does.
        pipeline.add(new BasicDBObject("$unwind", "$metadata"));

        // Second, find the particular file that we're looking for, and it should not be more recent than the given timestamp
        SimpleDateFormat sdf  = new SimpleDateFormat("MM/dd/yy");
        Date date;
        if (time == null)
            date = new Date();
        else
            // Try parsing timestamp in both MM/dd/yy format and long format
            try {date = new Date(Long.parseLong(time)); }
            catch (NumberFormatException nfe) {
                try {date = new Date(sdf.parse(time).getTime() + MILLISECOND_IN_A_DAY);}
                catch (ParseException pe) {
                    System.out.println("Error: Syntax error in timestamp");
                    return;
                }
            }
        pipeline.add(new BasicDBObject("$match", new BasicDBObject("file", file).append("metadata." + TIMESTAMP, new BasicDBObject("$lte", date))));

        // Third, sort by timestamp in descending order
        pipeline.add(new BasicDBObject("$sort", new BasicDBObject("metadata." + TIMESTAMP, -1)));

        // Finally, limit the output to 1, so it only displays the most recent metadata before the specified timestamp
        pipeline.add(new BasicDBObject("$limit", 1));

        // Execute the query
        AggregateIterable<Document> results = collection.aggregate(pipeline);

        boolean resultFound = false;
        SimpleDateFormat outFormat = new SimpleDateFormat("MM/dd/yy HH:mm:ss");

        // Print the results
        for (Document d : results) {
            resultFound = true;
            Document meta = (Document) d.get("metadata");
            Date commitTime = (Date) meta.remove(TIMESTAMP);
            System.out.println(String.format("(Committed on %s) %s -> %s", outFormat.format(commitTime), d.get("file").toString(), meta.toJson()));
        }

        if (!resultFound)
            System.out.println("Result not found");
    }

    /**
     *
     *
     * @param namespace
     * @param keyword
     */
    public void find(String namespace, String query)
    {
        find(namespace, query, null);
    }


    /**
     * This function allows the user to view all files with a particular key-value pair from the database
     * (Assumes the metadata is at most one degree nested)
     *
     * @param namespace
     * @param keyword
     * @param time
     */
    public void find(String namespace, String query, String time)
    {
        // Retrieve the collection of documents in the specified namespace
        MongoCollection<Document> collection = database.getCollection(namespace);

        // A list to hold the pipeline steps for MongoDB's aggregate
        List<BasicDBObject> pipeline = new ArrayList<BasicDBObject>();

        // First, unwind the metadata array. See MongoDB documentation to understand what unwind does.
        pipeline.add(new BasicDBObject("$unwind", "$metadata"));

        // Second, give an upper bound for timestamp
        SimpleDateFormat sdf  = new SimpleDateFormat("MM/dd/yy");
        Date date;
        if (time == null)
            date = new Date();
        else
            // Try parsing timestamp in both MM/dd/yy format and long format
            try {date = new Date(Long.parseLong(time)); }
            catch (NumberFormatException nfe) {
                try {date = new Date(sdf.parse(time).getTime() + MILLISECOND_IN_A_DAY);}
                catch (ParseException pe) {
                    System.out.println("Error: Syntax error in timestamp");
                    return;
                }
            }
        pipeline.add(new BasicDBObject("$match", new BasicDBObject("metadata." + TIMESTAMP, new BasicDBObject("$lte", date))));

        // Third, sort by timestamp in descending order
        pipeline.add(new BasicDBObject("$sort", new BasicDBObject("metadata." + TIMESTAMP, -1)));

        // Fourth, group by file name, and only pick the first metadata (because it should be the most recent one, since it has been sorted)
        pipeline.add(new BasicDBObject("$group", new BasicDBObject("_id", "$file").append("metadata", new BasicDBObject("$first", "$metadata"))));

        // Finally, match the specified query
        pipeline.add(new BasicDBObject("$match", Document.parse(query)));

        // Execute the query
        AggregateIterable<Document> results = collection.aggregate(pipeline);

        int resultFound = 0;
        SimpleDateFormat outFormat = new SimpleDateFormat("MM/dd/yy HH:mm:ss");

        // Print the results
        for (Document d : results) {
            resultFound++;
            Document meta = (Document) d.get("metadata");
            Date commitTime = (Date) meta.remove(TIMESTAMP);
            System.out.println(String.format("(Committed on %s) %s -> %s", outFormat.format(commitTime), d.get("_id").toString(), meta.toJson()));
        }

        if (resultFound == 0)
            System.out.println("Result not found");
        else
            System.out.println(resultFound + " results found");
    }


    /**
     * This function allows the user to delete a namespace from the database
     *
     * @param namespace
     */
    public void clear(String namespace)
    {
        // Retrieve the collection of documents in the specified namespace
        MongoCollection<Document> collection = database.getCollection(namespace);
        // Drop the collection
        collection.drop();

        // Print confirmation message
        System.out.println("Repo " + namespace + " has been cleared");
    }
}
