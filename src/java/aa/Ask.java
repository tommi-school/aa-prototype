package aa;

import java.text.SimpleDateFormat;
import java.util.Date;

// represents an Ask (in a sell order)
public class Ask {

  private int id;
  private String stock;
  private int price; // ask price
  private String userId; // user who made this sell order
  private Date date;

  // constructor
  public Ask(String stock, int price, String userId) {
    this.stock = stock;
    this.price = price;
    this.userId = userId;
    this.date = new Date();
  }
  
  public Ask(int id, String stock, int price, String userId, Date date) {
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