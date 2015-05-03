package edu.berkeley.MetadataRepo;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;
import com.mongodb.MongoClient;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * A class that connects to a MongoDB database and handles all interactions with the database
 */
public class MetadataRepo
{
    private static final String TIMESTAMP = "__timestamp__";

    private MongoClient mongoClient;
    private MongoDatabase database;

    // Constructor
    public MetadataRepo(String address)
    {
        mongoClient = new MongoClient(address);
        database = mongoClient.getDatabase("MetadataRepo");
    }

    // Function that parses the user command and executes it accordingly
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
                /* Parameters for 'commit':
                *   cmd[1]: String namespace
                *   cmd[2]: String file
                *   cmd[3]: String jsonMetadata
                *   cmd[4]: long timestamp */
                commit(cmds[1], cmds[2], cmds[3], Long.parseLong(cmds[4]));
            }
            // User can view all metadata in the database
            else if (act.equals("dump"))
            {
                dump();
            }
            // User can view a particular file from the database
            else if (act.equals("show"))
            {
                /* Parameters for 'show':
                *   cmd[1]: String namespace
                *   cmd[2]: String file */
                show(cmds[1], cmds[2]);
                // NOTE: Should also implement time in 'show', similar to 'find' (see below)
                //   --> allow the user to query for legacy metadata (metadata other than the most recent)
            }
            // User can view all files with a particular key-value pair from the database
            else if (act.equals("find"))
            {
                /* Parameters for 'find':
                *   cmd[1]: String namespace
                *   cmd[2]: String keyword  (format: "key"="value")
                *   cmd[3]: String time
                *           (optional: searches the time range from beginning of history to time specified) */
                if (cmds.length == 4)
                    find(cmds[1], cmds[2], cmds[3]);
                else
                    find(cmds[1], cmds[2], "None");
            }
            // User can delete a namespace from the database
            else if (act.equals("clear"))
            {
                /* Parameters for 'clear':
                *   cmd[1]: String namespace */
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

    // * This function allows the user to enter metadata into the database *
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
        System.out.println("=======================================================================");
        System.out.println("Committed '" + file + "' to namespace '" + namespace + "'");
        System.out.println("=======================================================================");
    }

    // * This function allows the user to view all metadata in the database *
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

    // * This function allows the user to view a particular file from the database *
    public void show(String namespace, String file)
    {
        // Retrieve the collection of documents in the specified namespace
        MongoCollection<Document> collection = database.getCollection(namespace);

        // Find a document with the given name
        Document fdoc = new Document("file", file);
        FindIterable<Document> found = collection.find(fdoc);
        // NOTE: consider using 'collection.findOne' instead of 'collection.find'
        //      The former will return "only the first result from the result set,
        //      and not a MongoCursor that can be iterated over"

        System.out.println("=======================================================================");
        System.out.println("Namespace: " + namespace);
        System.out.println("-----------------------------------------------------------------------");

        if (found.iterator().hasNext()) {
            // If a document is found, it should be the only one
            Document doc = found.iterator().next();
            ArrayList<Document> metadataList  = (ArrayList<Document>) doc.get("metadata");

            // Get size of metadata array (last element should be the most recently committed entry)
            int currMetadataIndex = metadataList.size() - 1;

            // Get the date of the most recently committed entry
            long longDate = ((Date) metadataList.get(currMetadataIndex).get(TIMESTAMP)).getTime();
            SimpleDateFormat sdfDate = new SimpleDateFormat("dd-MMMM-yyyy, HH:mm:ss.SSS");
            String strDate = sdfDate.format(longDate);
            // Print the date
            System.out.println("Most recent metadata for " + file + " (committed on " + strDate + "):");
            // Print the most recent metadata
            System.out.println(metadataList.get(currMetadataIndex).toJson());

            /*
            // NOTE: This section of code would show ALL metadata commits ever for the document
            System.out.println("Metadata for " + file + ":");
            for (Document d : metadataList)
                System.out.println(d.toJson());
            */
        }
        else {
            // Print error message
            System.out.println("No file with the name: " + file + ".");
        }

        System.out.println("=======================================================================");
    }

    // * This function allows the user to view all files with a particular key-value pair from the database *
    //      (Assumes the metadata is at most one degree nested)
    public void find(String namespace, String keyword, String time)
    {
        // Initialize variables for checking the date
        boolean checkTime = false;
        long endTime = 0;
        Date date;
        SimpleDateFormat sdf  = new SimpleDateFormat("MM/dd/yy");

        if (!time.equals("None")) {
            // User has entered a value for the 'time' parameter

            // Set 'checkTime' flag
            checkTime = true;

            // Ensure that the time entered is in the appropriate format
            try {
                date = sdf.parse(time);
            } catch (ParseException e) {
                System.out.println("Time should be in MM/dd/yy format.");
                return;
            }

            // Set the end time of our search as the end-of-day of the specified date
            endTime = date.getTime() + 86400000;
            // NOTE: 86400000 equals the number of milliseconds in a day (1000*60*60*24)
        }

        // Retrieve the key and value pair that the user specifies
        //      (Assumes the format for the 'keyword' parameter as: "key"="value")
        String[] kv_pair = keyword.split("=");

        // Retrieve the collection of documents in the specified namespace
        MongoCollection<Document> collection = database.getCollection(namespace);

        // Initialize variables for checking the query
        BasicDBObject query;
        String fileName;
        int fileCount = 0;
        long compTime = 0;
        MongoCursor k;

        if (!checkTime) {
            // 'checkTime' is false: the user did not provide the optional 'time' parameter

            if (kv_pair[1].equals("*")) {
                // Query on metadata with the specified key and any value
                query = new BasicDBObject("metadata", new BasicDBObject("$elemMatch",
                            new BasicDBObject(kv_pair[0], new BasicDBObject("$exists",true))));
            } else {
                // Query on metadata with the specified key-value pair
                query = new BasicDBObject("metadata", new BasicDBObject("$elemMatch",
                            new BasicDBObject(kv_pair[0], kv_pair[1])));
            }
            // Get a cursor (pointer) to the documents containing the query
            FindIterable<Document> cursor = collection.find(query);
            // Set k to the head of the iterator of documents
            k = cursor.iterator();

        } else {
            // 'checkTime' is true: the user provided the optional 'time' parameter

            if (kv_pair[1].equals("*")) {
                // Query on metadata with the specified key and any value;
                //  searches from beginning of history to the time specified
                query = new BasicDBObject("metadata", new BasicDBObject("$elemMatch",
                            new BasicDBObject(TIMESTAMP, new BasicDBObject("$lte", new Date(endTime)))
                                    .append(kv_pair[0], new BasicDBObject("$exists", true))));
            } else {
                // Query on metadata with the specified key-value pair;
                //  searches from beginning of history to the time specified
                query = new BasicDBObject("metadata", new BasicDBObject("$elemMatch",
                            new BasicDBObject(TIMESTAMP, new BasicDBObject("$lte", new Date(endTime)))
                                    .append(kv_pair[0], kv_pair[1])));
            }
            // Get a cursor (pointer) to the documents containing the query
            FindIterable<Document> cursor = collection.find(query);
            // Set k to the head of the iterator of documents
            k = cursor.iterator();
        }

        System.out.println("=======================================================================");
        System.out.println("Namespace: " + namespace);
        System.out.println("-----------------------------------------------------------------------");

        while (k.hasNext()) {
            // Get the name of the document file
            Document d = (Document) k.next();
            ArrayList<Document> metadataList = (ArrayList<Document>) d.get("metadata");
            fileName = (String) d.get("file");

            long maxxTime = 0;
            int maxxInt = 0;

            for (int i = 0; i < metadataList.size(); i++) {
                compTime =  ((Date) metadataList.get(i).get(TIMESTAMP)).getTime();
                if ( ( checkTime && (compTime <= endTime && compTime > maxxTime) ) ||
                        ( !checkTime && ( compTime > maxxTime ) )) {
                    // Set 'maxxTime' to be either the time of the latest commit up to the specified 'time'
                    //  or the time of the most recent commit
                    maxxTime = compTime;
                    // Set maxxInt to be the index of 'metadataList' that 'maxxTime' is at
                    maxxInt = i;
                }
            }

            if (metadataList.get(maxxInt).get(kv_pair[0]) != null) {
                if (kv_pair[1].equals("*") || metadataList.get(maxxInt).get(kv_pair[0]).equals(kv_pair[1])
                    || (metadataList.get(maxxInt).get(kv_pair[0]).getClass() == ArrayList.class
                        && ((ArrayList<String>) metadataList.get(maxxInt).get(kv_pair[0])).contains(kv_pair[1]))) {
                    // Find the name of the file at the index calculated in the loop above
                    System.out.println("In file " + fileName + ":");
                    System.out.println(metadataList.get(maxxInt).toJson());
                    // Increment the count of files that contain the query on 'keyword'
                    fileCount++;
                }
            }
        }

        System.out.println(fileCount + " records found.");
        System.out.println("=======================================================================");
    }

    // * This function allows the user to delete a namespace from the database *
    public void clear(String namespace)
    {
        // Retrieve the collection of documents in the specified namespace
        MongoCollection<Document> collection = database.getCollection(namespace);
        // Drop the collection
        collection.drop();

        // Print confirmation message
        System.out.println("=======================================================================");
        System.out.println("Repo " + namespace + " has been cleared");
        System.out.println("=======================================================================");
    }
}
