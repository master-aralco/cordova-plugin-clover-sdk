package com.tituspeterson.cordova.cloversdk;

import com.clover.connector.sdk.v3.PaymentConnector;
import com.clover.sdk.util.Platform2;
import com.clover.sdk.v1.printer.Category;
import com.clover.sdk.v1.printer.Printer;
import com.clover.sdk.v1.printer.PrinterConnector;
import com.clover.sdk.v1.printer.TypeDetails;
import com.clover.sdk.v1.printer.job.PrintJobsConnector;
import com.clover.sdk.v1.printer.job.TextPrintJob;
import com.clover.sdk.v1.printer.job.ViewPrintJob;
import com.clover.sdk.v3.connector.ExternalIdUtils;
import com.clover.sdk.v3.connector.IPaymentConnectorListener;
import com.clover.sdk.v3.remotepay.AuthResponse;
import com.clover.sdk.v3.remotepay.CapturePreAuthResponse;
import com.clover.sdk.v3.remotepay.CloseoutResponse;
import com.clover.sdk.v3.remotepay.ConfirmPaymentRequest;
import com.clover.sdk.v3.remotepay.ManualRefundRequest;
import com.clover.sdk.v3.remotepay.ManualRefundResponse;
import com.clover.sdk.v3.remotepay.PreAuthResponse;
import com.clover.sdk.v3.remotepay.ReadCardDataResponse;
import com.clover.sdk.v3.remotepay.RefundPaymentRequest;
import com.clover.sdk.v3.remotepay.RefundPaymentResponse;
import com.clover.sdk.v3.remotepay.RetrievePaymentResponse;
import com.clover.sdk.v3.remotepay.RetrievePendingPaymentsResponse;
import com.clover.sdk.v3.remotepay.SaleRequest;
import com.clover.sdk.v3.remotepay.SaleResponse;
import com.clover.sdk.v3.remotepay.TipAdded;
import com.clover.sdk.v3.remotepay.TipAdjustAuthResponse;
import com.clover.sdk.v3.remotepay.VaultCardResponse;
import com.clover.sdk.v3.remotepay.VerifySignatureRequest;
import com.clover.sdk.v3.remotepay.VoidPaymentRefundResponse;
import com.clover.sdk.v3.remotepay.VoidPaymentResponse;
import com.clover.sdk.v3.scanner.BarcodeScanner;
import com.clover.sdk.v1.Intents;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.clover.sdk.util.CloverAccount;
import com.clover.sdk.v1.printer.job.PrintJob;

/**
 * This class echoes a string called from JavaScript.
 */
public class CloverAndroidSDK extends CordovaPlugin {

    public static final String LOG_TAG = "CordovaPluginCloverSDK";
    public static final String BARCODE_BROADCAST = "com.clover.BarcodeBroadcast";
    private final BarcodeReceiver barcodeReceiver = new BarcodeReceiver();
    // Initialize the PaymentConnector with a listener
    private PaymentConnector paymentConnector = null;
    // Used to keep track of what context to call back to.
    private CallbackContext callbackContext;

    // Overrides (for barcode and main execute switch)

    @Override
    public void onStart() {
        super.onStart();
        registerBarcodeScanner();
        if(Platform2.isClover()){
            paymentConnector = initializePaymentConnector();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        unregisterBarcodeScanner();
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        switch (action) {
            case "getNewUID":
                getNewUID(callbackContext);
                return true;
            case "isClover":
                checkCloverDevice(callbackContext);
                return true;
            case "startScan":
                this.callbackContext = callbackContext;
                startBarcodeScanner();
                return true;
            case "printTextReceipt":
                this.callbackContext = callbackContext;
                printReceipt(args);
                return true;
            case "takePayment":
                this.callbackContext = callbackContext;
                takePayment(args);
                return true;
            case "refund":
                this.callbackContext = callbackContext;
                refund(args);
                return true;
        }
        return false;
    }

    // Check If Clover Device

    private void checkCloverDevice(CallbackContext callbackContext) {
        boolean isClover = Platform2.isClover();
        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, isClover);
        callbackContext.sendPluginResult(pluginResult);
    }

    // Barcode Scanning

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerBarcodeScanner() {
        this.cordova.getActivity().getApplicationContext().registerReceiver(barcodeReceiver, new IntentFilter(BARCODE_BROADCAST));
    }

    private void unregisterBarcodeScanner() {
        this.cordova.getActivity().getApplicationContext().unregisterReceiver(barcodeReceiver);
    }

    private static Bundle getBarcodeSetting(final boolean enabled) {
        final Bundle extras = new Bundle();
        extras.putBoolean(Intents.EXTRA_START_SCAN, enabled);
        extras.putBoolean(Intents.EXTRA_SHOW_PREVIEW, enabled);
        extras.putBoolean(Intents.EXTRA_SHOW_MERCHANT_PREVIEW, enabled);
        extras.putBoolean(Intents.EXTRA_LED_ON, false);
        return extras;
    }

    public void startBarcodeScanner() {
        registerBarcodeScanner();
        new BarcodeScanner(this.cordova.getActivity().getApplicationContext()).startScan(getBarcodeSetting(true));
    }

    public void stopBarcodeScanner() {
        unregisterBarcodeScanner();
        new BarcodeScanner(this.cordova.getActivity().getApplicationContext()).stopScan(getBarcodeSetting(false));
    }

    private class BarcodeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Logger.getAnonymousLogger().log(Level.INFO, "*********** action ****************");
            Logger.getAnonymousLogger().log(Level.INFO, action);
            if (action != null && action.equals(BARCODE_BROADCAST)) {
                stopBarcodeScanner();
                String barcode = intent.getStringExtra("Barcode");
                if (barcode != null) {
                    PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, barcode);
                    callbackContext.sendPluginResult(pluginResult);
                }
                Logger.getAnonymousLogger().log(Level.INFO, barcode);
//                unregisterBarcodeScanner();
            }

        }
    }

    // Print Receipt

    public boolean printReceipt(JSONArray args) {
        String receipt;
        try {
            receipt = args.getString(0);
        } catch (JSONException e) {
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Clover printReceipt: arg at array index 0 is not a string."));
            return false;
        }
        try {
            AppCompatActivity activity = this.cordova.getActivity();
            Account account = CloverAccount.getAccount(activity);
            PrinterConnector pc = new PrinterConnector(activity, account, null);
            List<Printer> printerList = pc.getPrinters(Category.RECEIPT);
            if (printerList.size() > 0) {
                Printer preferredPrinter = printerList.get(0);
                TypeDetails typeDetails = pc.getPrinterTypeDetails(preferredPrinter);
                int width = typeDetails.getNumDotsWidth();

//                // Create layout
//                LinearLayout mainLayout = new LinearLayout(activity);
//                mainLayout.setLayoutParams(new LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.MATCH_PARENT));
//                mainLayout.setOrientation(LinearLayout.VERTICAL);
//                mainLayout.setBackgroundColor(Color.WHITE);
//
//                // Append Body
//                LinearLayout body = new LinearLayout(activity);
//                RelativeLayout row = new RelativeLayout(activity);
//                body.setMinimumWidth(ViewGroup.LayoutParams.MATCH_PARENT);
//                row.setMinimumWidth(ViewGroup.LayoutParams.MATCH_PARENT);
//                TextView receiptTextView = new TextView(activity);
//                row.addView(receiptTextView);
//                receiptTextView.setText(receipt);
//                receiptTextView.setTypeface(Typeface.MONOSPACE);
//                receiptTextView.setTextSize(25);
//                body.addView(row);
//                mainLayout.addView(body);
//                PrintJob pj = new ViewPrintJob.Builder().view(mainLayout, width).build();
//                pj.print(activity, account, preferredPrinter);

                TextView tv = new TextView(activity);
                tv.setText(receipt);
                tv.setTextColor(Color.BLACK);
                tv.setPadding(0,0,0,0);
//                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
//                params.setMargins(0, 15, 5, 0);
//                params.alignWithParent = true;
//                tv.setLayoutParams(params);
//                tv.setGravity(Gravity.CENTER);
                tv.setTextScaleX(0.76f);
                tv.setTextSize(10);
                tv.setTypeface(Typeface.MONOSPACE);
                PrintJob pj = new ViewPrintJob.Builder().view(tv, width).build();
//                pj.print(activity, account, preferredPrinter);
                PrintJobsConnector pjc = new PrintJobsConnector(this.cordova.getActivity());
                pjc.print(pj);
            } else {
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Clover printReceipt: No Printer Found"));
                return false;
            }
        } catch (Exception e) {
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Clover printReceipt: " + e.getMessage()));
            return false;
        }
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, true));
        return true;
//        String receipt;
//        try {
//            receipt = args.getString(0);
//        } catch (JSONException e) {
//            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Clover printReceipt: arg at array index 0 is not a string."));
//            return false;
//        }
//        try {
//            PrintJob pj = new TextPrintJob.Builder().text(receipt).flag(PrintJob.FLAG_NONE).build();
////            pj.print(this.cordova.getActivity(), CloverAccount.getAccount(this.cordova.getActivity()));
//            PrintJobsConnector pjc = new PrintJobsConnector(this.cordova.getActivity());
//            pjc.print(pj);
//        } catch (Exception e) {
//            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Clover printReceipt: " + e.getMessage()));
//            return false;
//        }
//        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, true));
//        return true;
    }

    // Take Payment

    public void takePayment(JSONArray args) {
        Log.d(LOG_TAG, "Start Taking Payment!");
        long charge;
        String uid;
        try {
            charge = args.getLong(0);
        } catch (JSONException e) {
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Clover takePayment: arg at array index 0 is not an int."));
            return;
        }
        try {
            uid = args.getString(1);
        } catch (JSONException e) {
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Clover printReceipt: arg at array index 1 is not a string."));
            return;
        }
        SaleRequest saleRequest = setupSaleRequest(charge, uid);
        paymentConnector.sale(saleRequest);
    }

    // Take Payment

    public void refund(JSONArray args) {
        Log.d(LOG_TAG, "Start Refund!");
        long charge;
        String uid;
        try {
            charge = args.getLong(0);
        } catch (JSONException e) {
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Clover refund: arg at array index 0 is not an int."));
            return;
        }
        try {
            uid = args.getString(1);
        } catch (JSONException e) {
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Clover printReceipt: arg at array index 1 is not a string."));
            return;
        }

        ManualRefundRequest refundRequest = new ManualRefundRequest();
        refundRequest.setAmount(charge);
        refundRequest.setExternalId(uid);

        paymentConnector.manualRefund(refundRequest);
    }

    private PaymentConnector initializePaymentConnector() {
        Log.d(LOG_TAG, "Initializing Payment Connector");
        AppCompatActivity activity = this.cordova.getActivity();
        // Get the Clover account that will be used with the service; uses the GET_ACCOUNTS permission
        Account cloverAccount = CloverAccount.getAccount(activity);
        // Set your RAID as the remoteApplicationId
        String remoteApplicationId = "H2HYB1Y2GHG0Y.500NSN41WBM6G";

        //Implement the interface
        IPaymentConnectorListener paymentConnectorListener = new IPaymentConnectorListener() {
            @Override public void onDeviceDisconnected() {
                Log.d(LOG_TAG, "IPaymentConnectorListener: onDeviceDisconnected");
            }
            @Override public void onDeviceConnected() {
                Log.d(LOG_TAG, "IPaymentConnectorListener: onDeviceConnected");
            }
            @Override public void onPreAuthResponse(PreAuthResponse response) {
                Log.d(LOG_TAG, "IPaymentConnectorListener: onPreAuthResponse " + response.toString());
            }
            @Override public void onAuthResponse(AuthResponse response) {
                Log.d(LOG_TAG, "IPaymentConnectorListener: onAuthResponse " + response.toString());
            }
            @Override public void onTipAdjustAuthResponse(TipAdjustAuthResponse response) {
                Log.d(LOG_TAG, "IPaymentConnectorListener: onTipAdjustAuthResponse " + response.toString());
            }
            @Override public void onCapturePreAuthResponse(CapturePreAuthResponse response) {
                Log.d(LOG_TAG, "IPaymentConnectorListener: onCapturePreAuthResponse " + response.toString());
            }
            @Override public void onVerifySignatureRequest(VerifySignatureRequest request) {
                Log.d(LOG_TAG, "IPaymentConnectorListener: onVerifySignatureRequest " + request.toString());
            }
            @Override public void onConfirmPaymentRequest(ConfirmPaymentRequest request) {
                Log.d(LOG_TAG, "IPaymentConnectorListener: onConfirmPaymentRequest " + request.toString());
            }
            @Override
            public void onSaleResponse(SaleResponse response) {
                Log.d(LOG_TAG, "IPaymentConnectorListener: onSaleResponse " + response.toString());
                if(response.getSuccess()) {
                    callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, response.getJSONObject()));
                } else {
                    try {
                        callbackContext.sendPluginResult(
                            new PluginResult(PluginResult.Status.ERROR, new JSONObject()
                                .put("reason", response.getReason())
                                .put("message", response.getMessage())
                            )
                        );
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            @Override public void onManualRefundResponse(ManualRefundResponse response) {
                Log.d(LOG_TAG, "IPaymentConnectorListener: onManualRefundResponse " + response.toString());
                if(response.getSuccess()) {
                    callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, response.getJSONObject()));
                } else {
                    try {
                        callbackContext.sendPluginResult(
                            new PluginResult(PluginResult.Status.ERROR, new JSONObject()
                                .put("reason", response.getReason())
                                .put("message", response.getMessage())
                            )
                        );
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            @Override public void onRefundPaymentResponse(RefundPaymentResponse response) {
                Log.d(LOG_TAG, "IPaymentConnectorListener: RefundPaymentResponse " + response.toString());
            }
            @Override public void onTipAdded(TipAdded tipAdded) {
                Log.d(LOG_TAG, "IPaymentConnectorListener: onTipAdded " + tipAdded.toString());
            }
            @Override public void onVoidPaymentResponse(VoidPaymentResponse response) {
                Log.d(LOG_TAG, "IPaymentConnectorListener: onVoidPaymentResponse " + response.toString());
            }
            @Override public void onVaultCardResponse(VaultCardResponse response) {
                Log.d(LOG_TAG, "IPaymentConnectorListener: onVaultCardResponse " + response.toString());
            }
            @Override public void onRetrievePendingPaymentsResponse(RetrievePendingPaymentsResponse retrievePendingPaymentResponse) {
                Log.d(LOG_TAG, "IPaymentConnectorListener: onRetrievePendingPaymentsResponse " + retrievePendingPaymentResponse.toString());
            }
            @Override public void onReadCardDataResponse(ReadCardDataResponse response) {
                Log.d(LOG_TAG, "IPaymentConnectorListener: onReadCardDataResponse " + response.toString());
            }
            @Override public void onCloseoutResponse(CloseoutResponse response) {
                Log.d(LOG_TAG, "IPaymentConnectorListener: onCloseoutResponse " + response.toString());
            }
            @Override public void onRetrievePaymentResponse(RetrievePaymentResponse response) {
                Log.d(LOG_TAG, "IPaymentConnectorListener: onRetrievePaymentResponse " + response.toString());
            }
            @Override public void onVoidPaymentRefundResponse(VoidPaymentRefundResponse response) {
                Log.d(LOG_TAG, "IPaymentConnectorListener: onVoidPaymentRefundResponse " + response.toString());
            }
        };

        // Implement the other IPaymentConnector listener methods

        // Create the PaymentConnector with the context, account, listener, and RAID
        return new PaymentConnector(activity, cloverAccount, paymentConnectorListener, remoteApplicationId);
    }

    private SaleRequest setupSaleRequest(long charge, String uid) {
        Log.d(LOG_TAG, "Setting Up Sale Request");
        // Create a new SaleRequest and populate the required request fields
        SaleRequest saleRequest = new SaleRequest();
        saleRequest.setExternalId(uid); //required, but can be any string
        saleRequest.setAmount(charge);

        return saleRequest;
    }

    private void getNewUID(CallbackContext callbackContext) {
        String uid = ExternalIdUtils.generateNewID();
        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, uid);
        callbackContext.sendPluginResult(pluginResult);
    }

    // Refund Payment
}

