package aa;

import java.util.Date;
import java.text.SimpleDateFormat;

// represents a bid (in a buy order)
public class Bid {

  private int id;
  private String stock;
  private int price; // bid price
  private String userId; // user who made this buy order
  private Date date;

  // constructor
  public Bid(String stock, int price, String userId) {
    this.stock = stock;
    this.price = price;
    this.userId = userId;
    this.date = new Date();
  }
  
  public Bid(int id, String stock, int price, String userId, Date date) {
    this.id = id;
    this.stock = stock;
    this.price = price;
    this.userId = userId;
    this.date = date;
  }

  // getters
  public int getId() {
    return id;
  }
  
  public String getStock() {
    return stock;
  }

  public int getPrice() {
    return price;
  }

  public String getUserId() {
    return userId;
  }

  public Date getDate() {
    return date;
  }

  //setters
  public void setId(int id) {
      this.id = id;
  }
  
  public void setStock(String stock) {
      this.stock = stock;
  }

  public void setPrice(int price) {
      this.price = price;
  }

  public void setUserId(String userId) {
      this.userId = userId;
  }

  public void setDate(Date date) {
      this.date = date;
  }
  
  public String getFormattedDate() {
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      return sdf.format(date);
  }
 
  // toString
  public String toString() {
    return "stock: " + stock + ", price: " + price + ", userId: " + userId + ", date: " + date;
  }
}