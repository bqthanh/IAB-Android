package hbs.vn.iab.util;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import com.android.vending.billing.IInAppBillingService;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import hbs.vn.iab.model.Product;

import static android.app.Activity.RESULT_OK;

/**
 * Created by thanhbui on 2017/01/11.
 */

public class TradingCoinManager {
    private final String TAG = getClass().getSimpleName();

    //エラーコードを定義する
    public static final int ERROR_JSON_EXCEPTION = 100;
    public static final int ERROR_REMOTE_EXCEPTION = 101;
    public static final int ERROR_SENDER_INTENT_EXCEPTION = 102;

    //購入アイテム種類を定義する
    public static final String PURCHASE_TYPE_INAPP = "inapp";
    public static final String PURCHASE_TYPE_SUBS = "subs";

    public static final int PURCHASE_FLOW_REQUEST_CODE = 1020;
    private static final int API_VERSION = 3;
    private final String developerPayload = "75f7eba495e4fcded0180e7ce15f8db1";

    private Activity mActivity;
    private ServiceConnection mServiceConn;
    private IInAppBillingService mService;
    private TradingCoinManagerListener mListener;

    private static TradingCoinManager sInstance;

    public static TradingCoinManager getInstance() {
        if (sInstance == null) {
            sInstance = new TradingCoinManager();
        }
        return sInstance;
    }

    /**
     * アプリ内課金サビースを初期する
     */
    public boolean bindService(Activity activity, TradingCoinManagerListener listener) {
        DebugLog.d(TAG, "アプリ内課金サビースを初期する");

        if (activity == null) {
            return false;
        }

        this.mActivity = activity;
        this.mListener = listener;

        if (!isIabServiceAvailable()) {
            DebugLog.i(TAG, "In-app billing service unavailable !");
            //return false;
        }
        mServiceConn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mService = IInAppBillingService.Stub.asInterface(service);
                //アプリ内課金サビースの初期が成功した
                mListener.onServiceInitialized();

                DebugLog.d(TAG,"アプリ内課金サビースの初期が成功した");
            }
            @Override
            public void onServiceDisconnected(ComponentName name) {
                mService = null;

                DebugLog.d(TAG, "アプリ内課金サビースの初期が失敗した");
            }
        };
        //アプリ内課金トランザクションへのセキュリティー
        Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
        serviceIntent.setPackage("com.android.vending");
        mActivity.bindService(serviceIntent, mServiceConn, Context.BIND_AUTO_CREATE);
        return true;
    }

    /**
     * アプリ内課金サビースの利用可能を確認する
     */
    private boolean isIabServiceAvailable() {
        PackageManager packageManager = mActivity.getPackageManager();
        Intent intent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
        //intent.setPackage("com.android.vending");
        List<ResolveInfo> serviceList = packageManager.queryIntentServices(intent, 0);

        DebugLog.d(TAG, "Services: " + serviceList.toString());
        return (serviceList != null
                && serviceList.size() > 0);
    }

    /**
     * 項目購入を実施する
     */
    public boolean purchase(String productId, String purchaseType) {
        if (this.mActivity == null) {
            return false;
        }

        int responseCode = 0;
        try {
            DebugLog.d(TAG, "mActivity != null: " + (mActivity != null));
            Bundle buyIntentBundle = mService.getBuyIntent(API_VERSION, mActivity.getPackageName(),
                    productId, purchaseType, developerPayload);
            if (buyIntentBundle != null) {
                responseCode = buyIntentBundle.getInt("RESPONSE_CODE");
                if (responseCode == 0) {
                    PendingIntent pendingIntent = buyIntentBundle.getParcelable("BUY_INTENT");
                    mActivity.startIntentSenderForResult(pendingIntent.getIntentSender(),
                            PURCHASE_FLOW_REQUEST_CODE, new Intent(), 0, 0, 0);
                    return true;
                }
            }
        } catch (RemoteException e) {
            responseCode = ERROR_REMOTE_EXCEPTION;
        } catch (IntentSender.SendIntentException e) {
            responseCode = ERROR_SENDER_INTENT_EXCEPTION;
        }

        if (mListener != null) {
            mListener.onPurchaseError(responseCode);
        }
        return false;
    }

    /**
     * 購入済み項目一覧を取得する
     */
    public ArrayList getOwnedPurchaseList(String purchaseType, String continuationToken) {
        try {
            Bundle ownedItems = mService.getPurchases(API_VERSION, mActivity.getPackageName(),
                    purchaseType, continuationToken);
            int response = ownedItems.getInt("RESPONSE_CODE");
            if (response == 0) {
                ArrayList<String>  purchaseDataList = ownedItems.getStringArrayList(
                        "INAPP_PURCHASE_DATA_LIST");
                continuationToken = ownedItems.getString("INAPP_CONTINUATION_TOKEN");
                //continuationToken != nullの場合、その他の項目を取得する
                if (continuationToken != null) {
                    ArrayList<String> ret = getOwnedPurchaseList(purchaseType, continuationToken);
                    if (ret != null) {
                        purchaseDataList.addAll(ret);
                    }
                }
                return purchaseDataList;
            }
        } catch (RemoteException e) {
            DebugLog.i(TAG, e.getMessage());
        }
        return null;
    }

    /**
     * AsyncTaskでinapp項目だけ消費する
     */
    public void consumePurchaseWithAsync(String purchaseToken) {
        IabAsyncTask mTask = new IabAsyncTask(mService, new IabAsyncTaskCallback() {
            @Override
            public void onDownloadFinished(Object result) {
                boolean isSuccess = (boolean) result;
                if (mListener != null) {
                    mListener.onProductConsumed(isSuccess);
                }
            }
        });

        mTask.execute(IabAsyncTask.CONSUME_PURCHASE, purchaseToken);
    }

    /**
     * AsyncTaskで購入項目情報を取得する
     */
    public boolean getProductDetailsWithAsync(List<Product> productList, String purchaseType) {
        if (productList == null
                || productList.size() == 0) {
            if (mListener != null) {
                mListener.onGetProductDetailsFinished(null);
            }
            return false;
        }

        IabAsyncTask mTask = new IabAsyncTask(mService, new IabAsyncTaskCallback() {
            @Override
            public void onDownloadFinished(Object result) {
                if (mListener != null) {
                    ArrayList<Product> responseList = (ArrayList<Product>) result;
                    mListener.onGetProductDetailsFinished(responseList);
                }
            }
        });

        mTask.execute(IabAsyncTask.GET_SKU_DETAILS, productList, purchaseType);
        return true;
    }

    /**
     * 購入情報を処理する
     */
    public void handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK
                && requestCode == PURCHASE_FLOW_REQUEST_CODE) {
            int responseCode = data.getIntExtra("RESPONSE_CODE", 0);
            String purchaseData = data.getStringExtra("INAPP_PURCHASE_DATA");
            if (responseCode == 0
                    && purchaseData != null) {
                try {
                    JSONObject source = new JSONObject(purchaseData);
                    String payloadData = source.getString("developerPayload");
                    if (developerPayload.equals(payloadData)) {
                        if (mListener != null) {
                            mListener.onProductPurchased(purchaseData);
                        }
                        return;
                    }
                } catch (JSONException e) {
                    DebugLog.i(TAG, e.getMessage());
                }
            }
            if (mListener != null) {
                mListener.onPurchaseError(ERROR_JSON_EXCEPTION);
                return;
            }
        }
        if (mListener != null) {
            mListener.onPurchaseError(resultCode);
        }
    }

    /**
     * アプリ内課金への接続サビースを解除する
     */
    public void release() {
        if (mService != null) {
            mActivity.unbindService(mServiceConn);
            DebugLog.d(TAG, "Release unbind service");
        }
        mActivity = null;
    }

    public void detachListener() {
        mListener = null;
        DebugLog.d(TAG, "Detach listener");
    }

    /**
     * consumePurchase、getSkuDetails向け処理する
     */
    private class IabAsyncTask extends AsyncTask<Object, Void, Object> {
        private final String TAG = getClass().getSimpleName();

        static final int CONSUME_PURCHASE = 1;
        static final int GET_SKU_DETAILS = 2;

        private IabAsyncTaskCallback mCallback;
        private IInAppBillingService mBillingService;

        IabAsyncTask(IInAppBillingService service, IabAsyncTaskCallback callback) {
            this.mBillingService = service;
            this.mCallback = callback;
        }

        @Override
        protected Object doInBackground(Object... params) {
            int taskType = (int) params[0];

            switch (taskType) {
                case CONSUME_PURCHASE:
                    String purchaseToken = (String) params[1];
                    return consumePurchase(purchaseToken);

                case GET_SKU_DETAILS:
                    ArrayList<Product> productList = (ArrayList<Product>) params[1];
                    String purchaseType = (String) params[2];

                    ArrayList<String> productIdList = new ArrayList<>();
                    for (Product product : productList) {
                        if (purchaseType.equals(product.getPurchaseType())) {
                            productIdList.add(product.getProductId());
                        }
                    }

                    ArrayList<String> responseList = getProductDetails(productIdList, purchaseType);
                    if (responseList == null) return null;

                    //購入可能なアイテム一覧を保存する変数
                    ArrayList<String> responseProductIdList = new ArrayList<>();
                    for (String response : responseList) {
                        JSONObject object;
                        try {
                            object = new JSONObject(response);
                            String id = object.getString("productId");
                            //購入可能なアイテムを追加する
                            responseProductIdList.add(id);
                        } catch (JSONException e) {
                            DebugLog.i(TAG, e.getLocalizedMessage());
                        }
                    }
                    ArrayList<Product> availableProductList = new ArrayList<>();
                    for (String resId : responseProductIdList) {
                        for (Product product : productList) {
                            if (resId.equals(product.getProductId())) {
                                availableProductList.add(product);
                            }
                        }
                    }

                    DebugLog.i(TAG, "availableProductList size: " + availableProductList.size());
                    return availableProductList;

                default:
                    DebugLog.i(TAG, "Purchase type is invalid !");
                    break;
            }

            return null;
        }

        @Override
        protected void onCancelled(Object o) {
            super.onCancelled(o);
        }

        @Override
        protected void onPostExecute(Object result) {
            super.onPostExecute(result);
            mCallback.onDownloadFinished(result);
            DebugLog.i(TAG, "onPostExecute: " + result);
        }

        /**
         * inapp項目だけ消費する
         */
        private boolean consumePurchase(String purchaseToken) {
            try {
                int response = mBillingService.consumePurchase(API_VERSION, mActivity.getPackageName() , purchaseToken);
                if (response == 0) {
                    return true;
                }
            } catch (RemoteException e) {
                DebugLog.i(TAG, e.getMessage());
            }
            return false;
        }

        /**
         * 購入項目情報を取得する
         */
        private ArrayList getProductDetails(ArrayList<String> productIdList, String purchaseType) {
            try {
                Bundle querySku = new Bundle();
                querySku.putStringArrayList("ITEM_ID_LIST", productIdList);
                Bundle skuDetails = mBillingService.getSkuDetails(API_VERSION, mActivity.getPackageName(),
                        purchaseType, querySku);
                int response = skuDetails.getInt("RESPONSE_CODE");
                if (response == 0) {
                    ArrayList productDetails = skuDetails.getStringArrayList("DETAILS_LIST");
                    return productDetails;
                }
            } catch (RemoteException e) {
                DebugLog.i(TAG, e.getMessage());
            }

            return null;
        }
    }

    public interface IabAsyncTaskCallback {
        void onDownloadFinished(Object result);
    }

    public interface TradingCoinManagerListener {
        void onServiceInitialized();
        void onProductPurchased(String purchaseData);
        void onPurchaseError(int resultCode);
        void onProductConsumed(boolean isSuccess);
        void onGetProductDetailsFinished(List<Product> responseList);
    }
}
