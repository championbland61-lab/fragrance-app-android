package com.thefragranceapp.app;

import android.app.Activity;
import com.android.billingclient.api.*;
import com.getcapacitor.*;
import com.getcapacitor.annotation.*;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

@CapacitorPlugin(name = "Billing")
public class BillingPlugin extends Plugin implements PurchasesUpdatedListener {

    private BillingClient billingClient;
    private PluginCall pendingCall;

    @Override
    public void load() {
        billingClient = BillingClient.newBuilder(getContext())
            .setListener(this)
            .enablePendingPurchases()
            .build();

        connectBilling();
    }

    private void connectBilling() {
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                // Ready
            }

            @Override
            public void onBillingServiceDisconnected() {
                connectBilling(); // Retry on disconnect
            }
        });
    }

    @PluginMethod
    public void getProducts(PluginCall call) {
        JSArray productIds = call.getArray("productIds");
        List<QueryProductDetailsParams.Product> productList = new ArrayList<>();

        try {
            for (int i = 0; i < productIds.length(); i++) {
                productList.add(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productIds.getString(i))
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                );
            }
        } catch (Exception e) {
            call.reject("Invalid product IDs: " + e.getMessage());
            return;
        }

        billingClient.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(productList).build(),
            (billingResult, productDetailsList) -> {
                JSObject result = new JSObject();
                JSArray products = new JSArray();

                for (ProductDetails details : productDetailsList) {
                    JSObject product = new JSObject();
                    product.put("productId", details.getProductId());
                    product.put("title", details.getTitle());
                    product.put("description", details.getDescription());

                    List<ProductDetails.SubscriptionOfferDetails> offers = details.getSubscriptionOfferDetails();
                    if (offers != null && !offers.isEmpty()) {
                        List<ProductDetails.PricingPhase> phases = offers.get(0).getPricingPhases().getPricingPhaseList();
                        if (!phases.isEmpty()) {
                            product.put("price", phases.get(0).getFormattedPrice());
                            product.put("priceCurrencyCode", phases.get(0).getPriceCurrencyCode());
                        }
                    }
                    products.put(product);
                }

                result.put("products", products);
                call.resolve(result);
            }
        );
    }

    @PluginMethod
    public void purchase(PluginCall call) {
        String productId = call.getString("productId");
        if (productId == null) {
            call.reject("productId is required");
            return;
        }
        this.pendingCall = call;

        List<QueryProductDetailsParams.Product> productList = List.of(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        );

        billingClient.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(productList).build(),
            (billingResult, productDetailsList) -> {
                if (productDetailsList.isEmpty()) {
                    call.reject("Product not found");
                    this.pendingCall = null;
                    return;
                }

                ProductDetails details = productDetailsList.get(0);
                List<ProductDetails.SubscriptionOfferDetails> offers = details.getSubscriptionOfferDetails();
                if (offers == null || offers.isEmpty()) {
                    call.reject("No subscription offers available");
                    this.pendingCall = null;
                    return;
                }

                List<BillingFlowParams.ProductDetailsParams> params = List.of(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offers.get(0).getOfferToken())
                        .build()
                );

                BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(params)
                    .build();

                Activity activity = getActivity();
                billingClient.launchBillingFlow(activity, flowParams);
            }
        );
    }

    @Override
    public void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
        if (pendingCall == null) return;

        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase purchase : purchases) {
                acknowledgePurchase(purchase);
            }
            JSObject result = new JSObject();
            result.put("success", true);
            pendingCall.resolve(result);
        } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
            pendingCall.reject("Purchase cancelled by user");
        } else {
            pendingCall.reject("Purchase failed: " + billingResult.getDebugMessage());
        }
        pendingCall = null;
    }

    private void acknowledgePurchase(Purchase purchase) {
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged()) {
            AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.getPurchaseToken())
                .build();
            billingClient.acknowledgePurchase(params, billingResult -> {});
        }
    }

    @PluginMethod
    public void restorePurchases(PluginCall call) {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            (billingResult, purchases) -> {
                JSObject result = new JSObject();
                JSArray purchasesArray = new JSArray();

                for (Purchase purchase : purchases) {
                    if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                        JSObject p = new JSObject();
                        p.put("purchaseToken", purchase.getPurchaseToken());
                        p.put("products", purchase.getProducts().toString());
                        purchasesArray.put(p);
                    }
                }

                result.put("purchases", purchasesArray);
                call.resolve(result);
            }
        );
    }
}
