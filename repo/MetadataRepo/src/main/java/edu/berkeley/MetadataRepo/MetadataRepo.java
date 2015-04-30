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

    public MetadataRepo(String address)
    {
        mongoClient = new MongoClient(address);
        database = mongoClient.getDatabase("MetadataRepo");
    }


    public void execute(String command)
    {
        if (command.length() == 0)
            return;

        String[] cmds = command.split(" ");
        String act =  cmds[0];

        try
        {
            if (act.equals("commit"))
            {
                commit(cmds[1], cmds[2], cmds[3], Long.parseLong(cmds[4]));
            }
            else if (act.equals("dump"))
            {
                dump();
            }
            else if (act.equals("show"))
            {
                show(cmds[1], cmds[2]);
            }
            else if (act.equals("find"))
            {
                if (cmds.length == 4)
                    find(cmds[1], cmds[2], cmds[3]);
                else
                    find(cmds[1], cmds[2], "None");
            }
            else if (act.equals("clear"))
            {
                clear(cmds[1]);
            }
            else
            {
                System.out.println("Error: Unrecognized command");
            }
        }
        catch (Exception e)
        {
            System.out.println("Error: Syntax error in command");
            System.out.println(e.toString());
        }
    }


    public void dump()
    {
        for (String namespace : database.listCollectionNames()) {
            if (namespace.equals("system.indexes"))
                continue;
            MongoCollection<Document> collection = database.getCollection(namespace);
            System.out.println("=======================================================================");
            System.out.println("Namespace: " + namespace);
            System.out.println("-----------------------------------------------------------------------");
            for (Document d : collection.find())
                System.out.println(d.toJson());
            System.out.println("=======================================================================");
        }
    }

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
            metadata.append(TIMESTAMP, new Date(timestamp));
            metadataList.add(metadata);

            // Updates the metadata
            collection.updateOne(fdoc, new Document("$set", new Document("metadata", metadataList)));
        }
        else
        {
            // Document not found, creating new document
            Document doc = new Document();
            doc.append("file", file);
            ArrayList<Document> metadataList = new ArrayList<Document>();
            Document metadata = Document.parse(jsonMetadata);
            metadata.append(TIMESTAMP, new Date(timestamp));
            metadataList.add(metadata);
            doc.append("metadata", metadataList);

            // Inserts the new document to the db
            collection.insertOne(doc);
        }

        System.out.println("Committed '" + file + "' to namespace '" + namespace + "'");
    }

    public void show(String namespace, String file)
    {
        MongoCollection<Document> collection = database.getCollection(namespace);

        // Find a document with the given name
        Document fdoc = new Document("file", file);
        FindIterable<Document> found = collection.find(fdoc);

        if (found.iterator().hasNext()) {
            // If a document is found, it should be the only one
            Document doc = found.iterator().next();
            ArrayList<Document> metadataList  = (ArrayList<Document>) doc.get("metadata");

            /*
            // This section of code would show ALL metadata commits ever
            System.out.println("Metadata for " + file + ":");
            for (Document d : metadataList)
                System.out.println(d.toJson());
            */
            System.out.println("Most recent metadata for " + file + ":");
            // Get size of metadata array (last element show be the most up-to-date entry
            int currMetadataIndex = metadataList.size() - 1;
            System.out.println(metadataList.get(currMetadataIndex).toJson());
        }
        else
        {
            System.out.println("No file with that name.");
        }

    }

    /**
     * Assumes the metadata is at most one degree nested
     * */
    public void find(String namespace, String keyword, String time)
    {
        boolean checkTime = false;
        long startTime = 0;
        long endTime = 0;
        Date date;
        SimpleDateFormat sdf  = new SimpleDateFormat("MM/dd/yy");

        if (!time.equals("None")) {
            checkTime = true;
            try {
                date = sdf.parse(time);
            } catch (ParseException e) {
                System.out.println("Time should be in MM/dd/yy format.");
                return;
            }
            startTime = date.getTime();
            endTime = startTime + 86400000;

        }

        String[] temp = keyword.split("=");

        MongoCollection<Document> collection = database.getCollection(namespace);

        BasicDBObject query;
        String fileName;
        int count = 0;
        long compTime = 0;
        MongoCursor k;
        if (!checkTime) {
            if (temp[1].equals("*")) {
                query = new BasicDBObject("metadata", new BasicDBObject("$elemMatch",
                            new BasicDBObject(temp[0], new BasicDBObject("$exists",true))));
            } else {
                query = new BasicDBObject("metadata", new BasicDBObject("$elemMatch",
                            new BasicDBObject(temp[0], temp[1])));
            }
            FindIterable<Document> cursor = collection.find(query);
            k = cursor.iterator();
        } else {
            if (temp[1].equals("*")) {
                query = new BasicDBObject("metadata", new BasicDBObject("$elemMatch",
                            new BasicDBObject(TIMESTAMP, new BasicDBObject("$gt", new Date(startTime))
                                    .append("$lte", new Date(endTime))).append(temp[0], new BasicDBObject("$exists", true))));
            } else {
                query = new BasicDBObject("metadata", new BasicDBObject("$elemMatch",
                            new BasicDBObject(TIMESTAMP, new BasicDBObject("$gt", new Date(startTime))
                                    .append("$lte", new Date(endTime))).append(temp[0], temp[1])));
            }
            FindIterable<Document> cursor = collection.find(query);
            k = cursor.iterator();
        }


        System.out.println("=======================================================================");
        System.out.println("Namespace: " + namespace);
        System.out.println("-----------------------------------------------------------------------");
        while (k.hasNext()) {
            Document d = (Document) k.next();
            ArrayList<Document> metadataList = (ArrayList<Document>) d.get("metadata");
            fileName = (String) d.get("file");
            boolean firstT = false;
            for (int i = 0; i < metadataList.size(); i++) {
                if (metadataList.get(i).get(temp[0]) != null) {
                    if (temp[1].equals("*") || metadataList.get(i).get(temp[0]).equals(temp[1])
                            || (metadataList.get(i).get(temp[0]).getClass() == ArrayList.class
                                && ((ArrayList <String>) metadataList.get(i).get(temp[0])).contains(temp[1])) ) {
                        compTime =  ((Date) metadataList.get(i).get(TIMESTAMP)).getTime();
                        if ( checkTime && (compTime <= endTime && compTime > startTime) || !checkTime) {
                            if (!firstT) {
                                firstT = true;
                                System.out.println("In file " + fileName + ":");
                            }
                            System.out.println(metadataList.get(i).toJson());
                            count++;
                        }
                    }
                }
            }
        }
        System.out.println(count + " records found.");
        System.out.println("=======================================================================");
    }

    public void clear(String namespace)
    {
        MongoCollection<Document> collection = database.getCollection(namespace);
        collection.drop();
        System.out.println("Repo " + namespace + " has been cleared");
    }
}
