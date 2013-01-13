package aa;

import java.io.*;
import java.util.*;
import java.sql.*;

public class ExchangeBean {

    // change the dbURL if necessary.
    String dbURL = "jdbc:mysql://localhost:3306/exchange";
    String dbDriver = "com.mysql.jdbc.Driver";
    private Connection dbCon;
    // location of log files - change if necessary
    private final String MATCH_LOG_FILE = "c:\\temp\\matched.log";
    private final String REJECTED_BUY_ORDERS_LOG_FILE = "c:\\temp\\rejected.log";
    // used to calculate remaining credit available for buyers
    private final int DAILY_CREDIT_LIMIT_FOR_BUYERS = 1000000;
    // used for keeping track of unfulfilled asks and bids in the system.
    // once asks or bids are matched, they must be removed from these arraylists.
    private ArrayList<Ask> unfulfilledAsks = new ArrayList<Ask>();
    private ArrayList<Bid> unfulfilledBids = new ArrayList<Bid>();
    // used to keep track of all matched transactions (asks/bids) in the system
    // matchedTransactions is cleaned once the records are written to the log file successfully
    private ArrayList<MatchedTransaction> matchedTransactions = new ArrayList<MatchedTransaction>();
    // keeps track of the latest price for each of the 3 stocks
    private int latestPriceForSmu = -1;
    private int latestPriceForNus = -1;
    private int latestPriceForNtu = -1;
    // keeps track of the remaining credit limits of each buyer. This should be
    // checked every time a buy order is submitted. Buy orders that breach the
    // credit limit should be rejected and logged
    // The key for this Hashtable is the user ID of the buyer, and the corresponding value is the REMAINING credit limit
    // the remaining credit limit should not go below 0 under any circumstance!
    private Hashtable<String, Integer> creditRemaining = new Hashtable<String, Integer>();

    // connects to the database using root. change your database id/password here if necessary
    public boolean connect() throws ClassNotFoundException, SQLException {
        Class.forName(dbDriver);

        // login credentials to your MySQL server
        dbCon = DriverManager.getConnection(dbURL, "root", "");
        return true;
    }

    // closes the DB connection
    public void close() throws SQLException {
        dbCon.close();
    }

    // this method is called once at the end of each trading day. It can be called manually, or by a timed daemon
    // this is a good chance to "clean up" everything to get ready for the next trading day
    public void endTradingDay() throws SQLException {
        // reset attributes
        latestPriceForSmu = -1;
        latestPriceForNus = -1;
        latestPriceForNtu = -1;

        // dump all unfulfilled buy and sell orders
        updateSQL("delete from ask");
        updateSQL("delete from bid");

        // reset all credit limits of users
        creditRemaining.clear();
    }

    // returns a String of unfulfilled bids for a particular stock
    // returns an empty string if no such bid
    // bids are separated by <br> for display on HTML page
    public String getUnfulfilledBidsForDisplay(String stock) throws SQLException {
        String returnString = "";
        ResultSet rs = execSQL("select * from bid where stock='" + stock + "' and has_transacted is null");
        while (rs.next()) {
            Bid bid = new Bid(rs.getInt("id"), rs.getString("stock"), rs.getInt("price"),
                    rs.getString("user"), new java.util.Date(rs.getTimestamp("date").getTime()));
            returnString += bid.toString() + "<br />";;
        }
        return returnString;
    }

    // returns a String of unfulfilled asks for a particular stock
    // returns an empty string if no such ask
    // asks are separated by <br> for display on HTML page
    public String getUnfulfilledAsks(String stock) throws SQLException {
        String returnString = "";
        ResultSet rs = execSQL("select * from ask where stock='" + stock + "' and has_transacted is null");
        while (rs.next()) {
            Ask ask = new Ask(rs.getInt("id"), rs.getString("stock"), rs.getInt("price"), 
                    rs.getString("user"), new java.util.Date(rs.getTimestamp("date").getTime()));
            returnString += ask.toString() + "<br />";
        }
        return returnString;
    }

    // returns the highest bid for a particular stock
    // returns -1 if there is no bid at all
    public int getHighestBidPrice(String stock) throws SQLException {
        Bid highestBid = getHighestBid(stock);
        if (highestBid == null) {
            return -1;
        } else {
            return highestBid.getPrice();
        }
    }

    // retrieve unfulfiled current (highest) bid for a particular stock
    // returns null if there is no unfulfiled bid for this stock
    //if there are 2 bids of the same amount, the earlier one is considered the highest bid
    private Bid getHighestBid(String stock) throws SQLException {
        Bid highestBid = new Bid(null, 0, null);
        String sql = String.format(
                "select * from bid where stock='%s' and price=(select max(price) from bid where stock='%s' and has_transacted is null) and date=(select min(date) from bid where stock='%s' and price=(select max(price) from bid where stock='%s' and has_transacted is null));",
                stock, stock, stock, stock);

        ResultSet rs = execSQL(sql);
        while (rs.next()) {
            highestBid = new Bid(rs.getInt("id"), rs.getString("stock"), rs.getInt("price"),
                    rs.getString("user"), new java.util.Date(rs.getTimestamp("date").getTime()));
        }

        if (highestBid.getUserId() == null) {
            return null; // there's no unfulfilled bid at all!
        }
        return highestBid;
    }

    // returns the lowest ask for a particular stock
    // returns -1 if there is no ask at all
    public int getLowestAskPrice(String stock) throws SQLException {
        Ask lowestAsk = getLowestAsk(stock);
        if (lowestAsk == null) {
            return -1;
        } else {
            return lowestAsk.getPrice();
        }
    }

    // retrieve unfulfiled current (lowest) ask for a particular stock
    // returns null if there is no unfulfiled asks for this stock
    // if there are 2 asks of the same ask amount, the earlier one is considered the highest ask
    private Ask getLowestAsk(String stock) throws SQLException {
        Ask lowestAsk = new Ask(null, Integer.MAX_VALUE, null);
        String sql = String.format("select * from ask where stock='%s' and price=(select min(price) from ask where stock='%s' and has_transacted is null) and date=(select min(date) from ask where stock='%s' and price=(select min(price) from ask where stock='%s' and has_transacted is null));",
                stock, stock, stock, stock);

        ResultSet rs = execSQL(sql);
        while (rs.next()) {
            lowestAsk = new Ask(rs.getInt("id"), rs.getString("stock"), rs.getInt("price"),
                    rs.getString("user"), new java.util.Date(rs.getTimestamp("date").getTime()));
        }

        if (lowestAsk.getUserId() == null) {
            return null; // there's no unfulfilled asks at all!
        }
        return lowestAsk;
    }

    // get credit remaining for a particular buyer
    private int getCreditRemaining(String buyerUserId) {
        if (!(creditRemaining.containsKey(buyerUserId))) {
            // this buyer is not in the hash table yet. hence create a new entry for him
            creditRemaining.put(buyerUserId, DAILY_CREDIT_LIMIT_FOR_BUYERS);
        }
        return creditRemaining.get(buyerUserId);
    }

    // check if a buyer is eligible to place an order based on his credit limit
    // if he is eligible, this method adjusts his credit limit and returns true
    // if he is not eligible, this method logs the bid and returns false
    private boolean validateCreditLimit(Bid b) {
        // calculate the total price of this bid
        int totalPriceOfBid = b.getPrice() * 1000; // each bid is for 1000 shares
        int remainingCredit = getCreditRemaining(b.getUserId());
        int newRemainingCredit = remainingCredit - totalPriceOfBid;

        if (newRemainingCredit < 0) {
            // no go - log failed bid and return false
            logRejectedBuyOrder(b);
            return false;
        } else {
            // it's ok - adjust credit limit and return true
            creditRemaining.put(b.getUserId(), newRemainingCredit);
            return true;
        }
    }

    // call this to append all rejected buy orders to log file
    private void logRejectedBuyOrder(Bid b) {
        try {
            PrintWriter outFile = new PrintWriter(new FileWriter(REJECTED_BUY_ORDERS_LOG_FILE, true));
            outFile.append(b.toString() + "\n");
            outFile.close();
        } catch (IOException e) {
            // Think about what should happen here...
            System.out.println("IO EXCEPTIOn: Cannot write to file");
            e.printStackTrace();
        } catch (Exception e) {
            // Think about what should happen here...
            System.out.println("EXCEPTION: Cannot write to file");
            e.printStackTrace();
        }
    }

    // call this to append all matched transactions in matchedTransactions to log file and clear matchedTransactions
    private void logMatchedTransactions() {
        try {
            PrintWriter outFile = new PrintWriter(new FileWriter(MATCH_LOG_FILE, true));
            for (MatchedTransaction m : matchedTransactions) {
                outFile.append(m.toString() + "\n");
            }
            matchedTransactions.clear(); // clean this out
            outFile.close();
        } catch (IOException e) {
            // Think about what should happen here...
            System.out.println("IO EXCEPTIOn: Cannot write to file");
            e.printStackTrace();
        } catch (Exception e) {
            // Think about what should happen here...
            System.out.println("EXCEPTION: Cannot write to file");
            e.printStackTrace();
        }
    }

    // returns a string of HTML table rows code containing the list of user IDs and their remaining credits
    // this method is used by viewOrders.jsp for debugging purposes
    public String getAllCreditRemainingForDisplay() {
        String returnString = "";

        Enumeration items = creditRemaining.keys();

        while (items.hasMoreElements()) {
            String key = (String) items.nextElement();
            int value = creditRemaining.get(key);
            returnString += "<tr><td>" + key + "</td><td>" + value + "</td></tr>";
        }
        return returnString;
    }

    // call this method immediatley when a new bid (buying order) comes in
    // this method returns false if this buy order has been rejected because of a credit limit breach
    // it returns true if the bid has been successfully added
    public boolean placeNewBidAndAttemptMatch(Bid newBid) throws SQLException, ClassNotFoundException {
        // step 0: check if this bid is valid based on the buyer's credit limit
        boolean okToContinue = validateCreditLimit(newBid);
        if (!okToContinue) {
            return false;
        }

        // step 1: insert new bid into unfulfilledBids
        String sql = String.format(
                "insert into bid (stock, price, user, date) values ('%s', %s, '%s', '%s');",
                newBid.getStock(), newBid.getPrice(), newBid.getUserId(), newBid.getFormattedDate());
        updateSQL(sql);

        // step 2: check if there is any unfulfilled asks (sell orders) for the new bid's stock. if not, just return
        // count keeps track of the number of unfulfilled asks for this stock
        int count = 0;
        ResultSet rs = execSQL("select count(*) from ask where stock = '" + newBid.getStock() + "';");
        while (rs.next()) {
            count = rs.getInt(1);
        }

        if (count == 0) {
            return true; // no unfulfilled asks of the same stock
        }

        // step 3: identify the current/highest bid in unfulfilledBids of the same stock
        Bid highestBid = getHighestBid(newBid.getStock());

        // step 4: identify the current/lowest ask in unfulfilledAsks of the same stock
        Ask lowestAsk = getLowestAsk(newBid.getStock());

        // step 5: check if there is a match.
        // A match happens if the highest bid is bigger or equal to the lowest ask
        if (highestBid.getPrice() >= lowestAsk.getPrice()) {
            // a match is found!
            updateSQL("update bid set has_transacted=1 where id=" + highestBid.getId());  //remove highest bid
            updateSQL("update ask set has_transacted=1 where id=" + lowestAsk.getId());  //remove lowest ask

            // this is a BUYING trade - the transaction happens at the higest bid's timestamp, and the transaction price happens at the lowest ask
            MatchedTransaction match = new MatchedTransaction(highestBid, lowestAsk, highestBid.getDate(), lowestAsk.getPrice());
            String sqlMatchTransaction =
                    String.format("insert into transaction (bid_id, ask_id, date, price, stock) values (%d, %d, '%s', %d '%s');",
                    highestBid.getId(), lowestAsk.getId(), highestBid.getFormattedDate(), lowestAsk.getPrice(), highestBid.getStock());
            updateSQL(sqlMatchTransaction);

            // to be included here: inform Back Office Server of match
            // to be done in v1.0

            updateLatestPrice(match);
            logMatchedTransactions();
        }

        return true; // this bid is acknowledged
    }

    // call this method immediatley when a new ask (selling order) comes in
    public void placeNewAskAndAttemptMatch(Ask newAsk) throws SQLException {
        // step 1: insert new ask into unfulfilledAsks
        String sql = String.format(
                "insert into ask (stock, price, user, date) values ('%s', %s, '%s', '%s');",
                newAsk.getStock(), newAsk.getPrice(), newAsk.getUserId(), newAsk.getFormattedDate());
        updateSQL(sql);

        // step 2: check if there is any unfulfilled bids (buy orders) for the new ask's stock. if not, just return
        // count keeps track of the number of unfulfilled bids for this stock
        int count = 0;
        ResultSet rs = execSQL("select count(*) from bid where stock = '" + newAsk.getStock() + "';");
        while (rs.next()) {
            count = rs.getInt(1);
        }

        if (count == 0) {
            return; // no unfulfilled asks of the same stock
        }

        // step 3: identify the current/highest bid in unfulfilledBids of the same stock
        Bid highestBid = getHighestBid(newAsk.getStock());

        // step 4: identify the current/lowest ask in unfulfilledAsks of the same stock
        Ask lowestAsk = getLowestAsk(newAsk.getStock());


        // step 5: check if there is a match.
        // A match happens if the lowest ask is <= highest bid
        if (lowestAsk.getPrice() <= highestBid.getPrice()) {
            // a match is found!
            updateSQL("update bid set has_transacted=1 where id=" + highestBid.getId());  //remove highest bid
            updateSQL("update ask set has_transacted=1 where id=" + lowestAsk.getId());  //remove lowest ask

            // this is a SELLING trade - the transaction happens at the lowest ask's timestamp, and the transaction price happens at the highest bid
            MatchedTransaction match = new MatchedTransaction(highestBid, lowestAsk, lowestAsk.getDate(), highestBid.getPrice());
            String sqlMatchTransaction =
                    String.format("insert into transaction (bid_id, ask_id, date, price, stock) values (%d, %d, '%s', %d, '%s');",
                    highestBid.getId(), lowestAsk.getId(), lowestAsk.getFormattedDate(), highestBid.getPrice(), highestBid.getStock());
            System.out.println(sqlMatchTransaction);
            updateSQL(sqlMatchTransaction);

            // to be included here: inform Back Office Server of match
            // to be done in v1.0

            updateLatestPrice(match);
            logMatchedTransactions();
        }
    }

    // updates either latestPriceForSmu, latestPriceForNus or latestPriceForNtu
    // based on the MatchedTransaction object passed in
    private void updateLatestPrice(MatchedTransaction m) {
        String stock = m.getStock();
        int price = m.getPrice();
        // update the correct attribute
        if (stock.equals("smu")) {
            latestPriceForSmu = price;
        } else if (stock.equals("nus")) {
            latestPriceForNus = price;
        } else if (stock.equals("ntu")) {
            latestPriceForNtu = price;
        }
    }

    // updates either latestPriceForSmu, latestPriceForNus or latestPriceForNtu
    // based on the MatchedTransaction object passed in
    public int getLatestPrice(String stock) {
        if (stock.equals("smu")) {
            return latestPriceForSmu;
        } else if (stock.equals("nus")) {
            return latestPriceForNus;
        } else if (stock.equals("ntu")) {
            return latestPriceForNtu;
        }
        return -1; // no such stock
    }

    // used to execute a generic select SQL statement
    public ResultSet execSQL(String sql) throws SQLException {
        Statement s = dbCon.createStatement();
        ResultSet r = s.executeQuery(sql);
        return (r == null) ? null : r;
    }

    // used to execute an update SQL statement.
    public int updateSQL(String sql) throws SQLException {
        Statement s = dbCon.createStatement();
        int r = s.executeUpdate(sql);
        return (r == 0) ? 0 : r;
    }
}
