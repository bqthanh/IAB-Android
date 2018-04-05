package hbs.vn.iab.adapter;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import hbs.vn.iab.R;
import hbs.vn.iab.model.Product;

/**
 * Created by thanhbui on 2017/01/31.
 */

public class ChargeListAdapter extends RecyclerView.Adapter<ChargeListAdapter.MyViewHolder> {
    private final String TAG = getClass().getSimpleName();

    private List<Product> mProductList;
    private ChargeListAdapterListener mListener;

    public ChargeListAdapter(List<Product> productList, ChargeListAdapterListener listener) {
        this.mProductList = productList;
        this.mListener = listener;
    }

    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_charge_view, parent, false);
        return new MyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(MyViewHolder holder, int position) {
        holder.updateItem(mProductList.get(position));
    }

    @Override
    public int getItemCount() {
        if (mProductList != null) {
            return mProductList.size();
        }
        return 0;
    }

    public class MyViewHolder extends RecyclerView.ViewHolder {

        @BindView(R.id.coin_view)
        TextView mCoinView;

        @BindView(R.id.btn_purchase)
        Button mPurchaseButton;

        public MyViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }

        public void updateItem(Product product) {
            mCoinView.setText("" + product.getCoin());
            mPurchaseButton.setText("¥" + product.getPrice());
        }

        @OnClick(R.id.btn_purchase)
        public void onPurchaseButtonClick() {
            int position = this.getAdapterPosition();
            if (mListener == null
                || position <= RecyclerView.NO_POSITION) {
                return;
            }

            Product item = mProductList.get(position);
            mListener.onPurchaseButtonClick(item.getProductId(), item.getPurchaseType());
        }
    }

    /**
     * Interface definition for callback on purchase clicked.
     */
    public interface ChargeListAdapterListener {
        void onPurchaseButtonClick(String productId, String purchaseType);
    }
}