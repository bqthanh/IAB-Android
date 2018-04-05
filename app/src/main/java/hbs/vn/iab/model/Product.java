package hbs.vn.iab.model;

/**
 * Created by thanhbui on 2017/01/31.
 */

public class Product {
    private static final String TAG = Product.class.getSimpleName();

    protected String productId;
    protected int price;
    protected int coin;
    protected String purchaseType;
    protected String description;

    public Product (String productId, int price, int coin, String purchaseType, String description) {
        this.productId = productId;
        this.price = price;
        this.coin = coin;
        this.purchaseType = purchaseType;
        this.description = description;
    }

    public String getProductId() {
        return productId;
    }

    public int getPrice() {
        return price;
    }

    public int getCoin() {
        return coin;
    }

    public String getPurchaseType() {
        return purchaseType;
    }

    public String getDescription() {
        return description;
    }
}