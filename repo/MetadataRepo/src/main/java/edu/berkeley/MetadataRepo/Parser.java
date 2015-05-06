package edu.berkeley.MetadataRepo;

import com.mongodb.BasicDBObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * A utility class that provides a function to parse queries
 */
public class Parser {

    private static final long MILLISECOND_IN_A_DAY = 86400000 - 1;

    public static Date parseTime(String time, boolean endOfDay)
    {
        time = time.trim();

        SimpleDateFormat sdf  = new SimpleDateFormat("MM/dd/yy");
        // Try parsing timestamp in both MM/dd/yy format and long format
        try {return new Date(Long.parseLong(time)); }
        catch (NumberFormatException nfe) {
            try {return new Date(sdf.parse(time).getTime() + (endOfDay? MILLISECOND_IN_A_DAY : 0));}
            catch (ParseException pe) {
                return null;
            }
        }
    }

    public static BasicDBObject parseGlob(String glob)
    {
        glob = glob.trim();
        glob = glob.replace(".", "\\.");
        glob = glob.replace("*", ".*");
        glob = glob.replace("?", ".");
        glob = "^" + glob + "$";

        return new BasicDBObject("$regex", glob);
    }

    public static BasicDBObject parseExpression(String expression)
    {
        expression = expression.trim();

        // Parse 'or'
        if (expression.toLowerCase().contains(" or "))
        {
            int i = expression.toLowerCase().indexOf("or");
            ArrayList<BasicDBObject> conditions = new ArrayList<BasicDBObject>();
            BasicDBObject left = parseExpression(expression.substring(0, i));
            BasicDBObject right = parseExpression(expression.substring(i + 3));
            if (left == null || right == null)
                return null;
            conditions.add(new BasicDBObject(left));
            conditions.add(new BasicDBObject(right));
            return new BasicDBObject("$or", conditions);
        }
        // Parse 'and'
        else if (expression.toLowerCase().contains(" and "))
        {
            int i = expression.toLowerCase().indexOf("and");
            ArrayList<BasicDBObject> conditions = new ArrayList<BasicDBObject>();
            BasicDBObject left = parseExpression(expression.substring(0, i));
            BasicDBObject right = parseExpression(expression.substring(i + 3));
            if (left == null || right == null)
                return null;
            conditions.add(new BasicDBObject(left));
            conditions.add(new BasicDBObject(right));
            return new BasicDBObject("$and", conditions);
        }
        // Parse '<='
        else if (expression.contains("<="))
        {
            int i = expression.indexOf("<=");
            String key = parseKey(expression.substring(0, i));
            Object value = parseValue(expression.substring(i + 2));
            if (key == null || value == null)
                return null;
            return new BasicDBObject(key, new BasicDBObject("$lte",value));
        }
        // Parse '>='
        else if (expression.contains(">="))
        {
            int i = expression.indexOf(">=");
            String key = parseKey(expression.substring(0, i));
            Object value = parseValue(expression.substring(i + 2));
            if (key == null || value == null)
                return null;
            return new BasicDBObject(key, new BasicDBObject("$gte",value));
        }
        // Parse '<'
        else if (expression.contains("<"))
        {
            int i = expression.indexOf("<");
            String key = parseKey(expression.substring(0, i));
            Object value = parseValue(expression.substring(i + 1));
            if (key == null || value == null)
                return null;
            return new BasicDBObject(key, new BasicDBObject("$lt",value));
        }
        // Parse '>'
        else if (expression.contains(">"))
        {
            int i = expression.indexOf(">");
            String key = parseKey(expression.substring(0, i));
            Object value = parseValue(expression.substring(i + 1));
            if (key == null || value == null)
                return null;
            return new BasicDBObject(key, new BasicDBObject("$gt",value));
        }
        // Parse '!='
        else if (expression.contains("!="))
        {
            int i = expression.indexOf("!=");
            String key = parseKey(expression.substring(0, i));
            Object value = parseValue(expression.substring(i + 2));
            if (key == null || value == null)
                return null;
            return new BasicDBObject(key, new BasicDBObject("$ne",value)).append(key, new BasicDBObject("$exists", true));
        }
        // Parse '='
        else if (expression.contains("="))
        {
            int i = expression.indexOf("=");
            String key = parseKey(expression.substring(0, i));
            Object value = parseValue(expression.substring(i + 1));
            if (key == null || value == null)
                return null;
            return new BasicDBObject(key, value);
        }

        return null;
    }

    private static String parseKey(String key)
    {
        return "metadata." + key.trim();
    }

    private static Object parseValue(String value)
    {
        value = value.trim();

        // Is it an explicit string?
        if (value.startsWith("\"") && value.endsWith("\""))
            return value.substring(1, value.length()-1);

        // Try parsing as numbers
        try {return Long.parseLong(value);} catch (Exception e) {}
        try {return Double.parseDouble(value);} catch (Exception e) {}

        // If all else fails, then it is an implicit string
        return value;
    }
}
