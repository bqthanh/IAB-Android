package hbs.vn.iab.ui;

import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import hbs.vn.iab.R;
import hbs.vn.iab.adapter.ChargeListAdapter;
import hbs.vn.iab.model.Product;
import hbs.vn.iab.util.DebugLog;
import hbs.vn.iab.util.TradingCoinManager;

public class MainActivity extends AppCompatActivity implements TradingCoinManager.TradingCoinManagerListener {
    private String TAG = MainActivity.class.getSimpleName();

    private final int CHARGE_COLUMN_NUM = 2;

    private TradingCoinManager mIabManager;
    private List<Product> mProductList;
    private String mNextPageUrl;

    private int mMyCoin;

    //アプリ内課金テスト
    public static final boolean IAB_TEST = true;
    int itemValue;
    final String PRODUCT_ID_PREFIX = "monst_anime_";
    public static List<String> RESERVED_PRODUCT_ID_LIST = Arrays.asList(
            "android.test.purchased",
            "android.test.canceled",
            "android.test.refunded",
            "android.test.item_unavailable"
    );

    @BindView(R.id.recycler_view)
    RecyclerView mRecyclerView;

    @BindView(R.id.text_mycoin)
    TextView mMyCoinText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);
        setTitle("In-app Billing");
        init();
   }

    /**
     * 表示データ、リスナー初期する
     */
    public void init() {
        mIabManager = TradingCoinManager.getInstance();
        mIabManager.bindService(this, this);

        List<Product> responseList;
        if (IAB_TEST == true) {
            responseList = genDummyResponseProductList();
        }

        this.mProductList = responseList;
        setProductList(this.mProductList);

        ChargeListAdapter adapter = new ChargeListAdapter(responseList, new ChargeListAdapter.ChargeListAdapterListener() {
            @Override
            public void onPurchaseButtonClick(String productId, String purchaseType) {
                if (IAB_TEST == true) {

                    itemValue = 0;
                    if (productId != null
                            && productId.contains("_")) {
                        try {
                            itemValue = Integer.valueOf(productId.substring(productId.lastIndexOf("_") + 1));
                            mIabManager.purchase(RESERVED_PRODUCT_ID_LIST.get(0),
                                    TradingCoinManager.PURCHASE_TYPE_INAPP);

                        } catch (Exception e) {
                            DebugLog.i(TAG, e.getLocalizedMessage());
                        }
                    }

                } else {
                    mIabManager.purchase(productId, purchaseType);
                }
                DebugLog.i(TAG, String.format("Product id: %s, purchase type: %s",
                        productId, purchaseType));
            }
        });
        mRecyclerView.addItemDecoration(new DividerItemDecoration());
        mRecyclerView.setLayoutManager(new GridLayoutManager(this, CHARGE_COLUMN_NUM));
        mRecyclerView.setAdapter(adapter);
    }

    /**
     * アプリ内課金サビースが準備したここから、処理をいれてください
     */
    @Override
    public void onServiceInitialized() {
        //TODO: 消費していないアイテムを消費する
        consumeUnconsumedProduct();

        if (IAB_TEST == true) {
            List<Product> productList = genDummyProductList();
            if (productList == null
                    || productList.size() <= 0) {
                return;
            }

            //サーバーからアイテム情報による購入可能なアイテムに対するクエリ
            mIabManager.getProductDetailsWithAsync(productList, TradingCoinManager.PURCHASE_TYPE_INAPP);

        } else {
            //TODO：ここから、サーバーからアイテム取得
            //TODO: サーバーからアイテム情報による購入可能なアイテムに対するクエリ
        }
    }

    @Override
    public void onProductPurchased(String purchaseData) {
        DebugLog.d(TAG, "purchaseData: " + purchaseData);

        try {
            JSONObject source = new JSONObject(purchaseData);
            String productId = source.optString("productId");
            String purchaseToken = source.optString("purchaseToken");

            if (IAB_TEST == true) {
                updateBalance(itemValue);
            } else {
                for (int i = 0; i < mProductList.size(); i++) {
                    Product data = mProductList.get(i);
                    if (productId.equals(data.getProductId())) {
                        updateBalance(data.getCoin());
                        break;
                    }
                }
            }

            //別のスレッドを作成し、その中でconsumePurchaseメソッドを呼び出し
            mIabManager.consumePurchaseWithAsync(purchaseToken);

            DebugLog.d("TAG", "Run to here !!");

        } catch (JSONException e) {
            DebugLog.i(TAG, e.getLocalizedMessage());
        }
        DebugLog.i(TAG, "Purchase is success");
    }

    @Override
    public void onPurchaseError(int resultCode) {
        DebugLog.i(TAG, "Purchase is fail with resultCode: " + resultCode);
    }

    @Override
    public void onProductConsumed(boolean isSuccess) {
        DebugLog.i(TAG, "onProductConsumed: " + isSuccess);

        //遷移元を指定すれば、元に戻る
        //TODO:
    }

    @Override
    public void onGetProductDetailsFinished(List<Product> responseList) {
        //For debug

    }

    /**
     * サーバーから取得したアイテム一覧を入手可能か確認した後、表示する
     */
    public void setProductList(List<Product> productList) {
        this.mProductList = productList;
        ChargeListAdapter adapter = (ChargeListAdapter) mRecyclerView.getAdapter();
        if (adapter != null) {
            //adapter.setProductList(this.mProductList);
        }
    }

    /**
     * アイテムを購入しても、まだ消費しない場合、実装する
     */
    private void consumeUnconsumedProduct() {
        ArrayList<String> mOwnedProductList = mIabManager.getOwnedPurchaseList(
                TradingCoinManager.PURCHASE_TYPE_INAPP, null);

        for (String purchaseData: mOwnedProductList) {
            //TODO: purchaseDataをサーバーに送る、サーバーが確認したら下記の消費をする

            DebugLog.i(TAG, "purchaseData: " + purchaseData);
            JSONObject object;
            try {
                object = new JSONObject(purchaseData);
                String purchaseToken = object.getString("purchaseToken");
                mIabManager.consumePurchaseWithAsync(purchaseToken);

            } catch (JSONException e) {
                DebugLog.i(TAG, e.getLocalizedMessage());
            }
        }
    }

    /**
     * 仮ストアアイテム一覧を作成する
     */
    public List<Product> genDummyResponseProductList() {
        List<Product> mProductList = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            int num = (i + 1) * 100;
            mProductList.add(new Product(PRODUCT_ID_PREFIX + num,
                    num,
                    num,
                    TradingCoinManager.PURCHASE_TYPE_INAPP,
                    "アプリ内課金"));
        }

        return mProductList;
    }

    /**
     * サーバーから仮アイテム一覧を作成する
     */
    public List<Product> genDummyProductList() {
        List<Product> mProductList = new ArrayList<>();

        for (int i = 0; i < 40; i++) {
            int num = (i + 1) * 100;

            String purchaseType = TradingCoinManager.PURCHASE_TYPE_INAPP;
            if (i >= 30) {
                purchaseType = TradingCoinManager.PURCHASE_TYPE_SUBS;
            }
            mProductList.add(new Product(PRODUCT_ID_PREFIX + num,
                    num,
                    num,
                    purchaseType,
                    "アプリ内課金"));
        }

        return mProductList;
    }

    /**
     * アプリ内課金が完了した後に残高を更新する
     */
    public void updateBalance(int coin) {
        mMyCoin += coin;
        mMyCoinText.setText(String.valueOf(mMyCoin));
        DebugLog.d(TAG, String.format("Purchase updated my coin: %s added %s", mMyCoin, coin));
    }

    /**
     * リサイクラービューに非対称カラムを処理するクラス
     */
    private class DividerItemDecoration extends RecyclerView.ItemDecoration {
        //リサイクラービューにカラムの間に距離
        private final int BORDER_SPACING = 90 / 2;

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
            super.getItemOffsets(outRect, view, parent, state);

            int position = parent.getChildAdapterPosition(view);
            if (position % 2 == 0) {
                outRect.right = BORDER_SPACING;
            } else {
                outRect.left = BORDER_SPACING;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case TradingCoinManager.PURCHASE_FLOW_REQUEST_CODE:
                mIabManager.handleActivityResult(requestCode, resultCode, data);
                break;

            default:
                break;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mIabManager.detachListener();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        TradingCoinManager.getInstance().release();
    }
}