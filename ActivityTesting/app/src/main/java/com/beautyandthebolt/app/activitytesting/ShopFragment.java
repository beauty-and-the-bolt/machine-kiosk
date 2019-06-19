package com.beautyandthebolt.app.activitytesting;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.NetworkResponse;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.UnsupportedEncodingException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import uk.me.hardill.volley.multipart.MultipartRequest;

public class ShopFragment extends Fragment {

    View fragmentView;
    private GridView gridView;
    private DisplayMetrics displayMetrics;
    private ShoppingCart cart;

    private float taxRate = 0.08f;
    private float perItemShipping = 0.5f;
    private float baseShipping = 4f;
    NumberFormat format;
    ViewGroup container;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container, Bundle savedInstanceState) {

        products = new Product[]{
                new Product(R.drawable.womens, getString(R.string.womens_name), 23, new String[]{"XS", "S", "M", "L", "XL", "2XL"},  getString(R.string.womens_description)),
                new Product(R.drawable.uni, getString(R.string.uni_name), 23, new String[]{"XS", "S", "M", "L", "XL", "2XL"},  getString(R.string.uni_description)),
                new Product(R.drawable.sweat, getString(R.string.sweat_name), 23, new String[]{"XS", "S", "M", "L", "XL", "2XL"},  getString(R.string.sweat_description)),
                new Product(R.drawable.mug, getString(R.string.mug_name), 15, null, getString(R.string.mug_description)),
                new Product(R.drawable.sock, getString(R.string.sock_name), 15, new String[]{"S", "M", "L"}, getString(R.string.sock_description)),
                new Product(R.drawable.tote, getString(R.string.tote_name), 15, null, getString(R.string.tote_description))
        };
        format = NumberFormat.getCurrencyInstance();

        this.container = container;
        // Set up Display Settings
        fragmentView = inflater.inflate(R.layout.fragment_shop, container, false);
        displayMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

        // Instantiate a shopping cart instance
        cart = new ShoppingCart(taxRate, baseShipping, perItemShipping);

        // Set up the item grid
        gridView = (GridView) fragmentView.findViewById(R.id.gridview);
        gridView.setNumColumns(getActivity().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 4 : 2);
        gridView.setAdapter(new ProductAdapter(container.getContext()));
        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                products[position].makeDetailDialog(container.getContext());
            }
        });

        setupFirebase();
        setupTwilio();

        Button cartButton = fragmentView.findViewById(R.id.cartButton);
        cartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cart.makeShoppingCartDialog();
            }
        });

        return fragmentView;
    }

    StorageReference storageRoot;
    StorageReference ordersThisDevice;
    String deviceName;

    public boolean setupFirebase(){
        try {
            FirebaseApp.initializeApp(fragmentView.getContext());
            storageRoot = FirebaseStorage.getInstance().getReference();
            deviceName = Settings.Secure.getString(getActivity().getContentResolver(), Settings.Secure.ANDROID_ID);
            Log.d("firebase-startup", deviceName);
            ordersThisDevice = storageRoot.child(deviceName+"-orders");
            FirebaseVisionFaceDetectorOptions.Builder options = new FirebaseVisionFaceDetectorOptions.Builder();
            options.setLandmarkType(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS);
            Log.d("firebase-startup", "yup");
            return true;
        }
        catch (Exception e){
            Log.d("firebase-startup", "nope: "+ e.toString());
            return false;
        }
    }

    RequestQueue twilioQueue;
    Map<String, String> twilioHeaders;
    String twilioBase = "https://api.twilio.com/2010-04-01/Accounts/";
    String accountSID = "AC90c91bd2afa942cc512ddc449a1a98b1";
    String accountAuthToken = "cc6fe6bf8219018bda15067c22e7b8e7";
    String twilioMode = "/Messages.json";
    String twilioSendNumber = "+12168684601";

    public boolean setupTwilio(){
        twilioQueue = Volley.newRequestQueue(getActivity());
        twilioHeaders = new HashMap<>();
        String credentials = accountSID + ":" + accountAuthToken;
        String auth = "Basic " + Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);
        twilioHeaders.put("Authorization", auth);
        return true;
    }

    public boolean sendTwilioSMS(String phoneNumber, String message){
        String url = twilioBase+accountSID+twilioMode;
        MultipartRequest request = new MultipartRequest(url, twilioHeaders,getPostResponseListener(), getPostErrorListener());
        request.addPart(new MultipartRequest.FormPart("From", twilioSendNumber));
        request.addPart(new MultipartRequest.FormPart("To", phoneNumber));
        request.addPart(new MultipartRequest.FormPart("Body", message));
        twilioQueue.add(request);
        return true;
    }

    private Response.Listener<NetworkResponse> getPostResponseListener(){
        return new Response.Listener<NetworkResponse>() {
            @Override
            public void onResponse(NetworkResponse networkResponse) {
                String json;
                if (networkResponse != null && networkResponse.data != null) {
                    try {
                        json = new String(networkResponse.data,
                                HttpHeaderParser.parseCharset(networkResponse.headers));
                        Log.d("twilio-startup", json);
                    } catch (UnsupportedEncodingException e) {
                        Log.d("twilio-startup", e.getMessage());
                    }
                }
            }
        };
    }

    private Response.ErrorListener getPostErrorListener(){
        return new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                String json;
                if (error.networkResponse != null && error.networkResponse.data != null) {
                    try {
                        json = new String(error.networkResponse.data,
                                HttpHeaderParser.parseCharset(error.networkResponse.headers));
                        Log.d("twilio-startup", json);
                    } catch (UnsupportedEncodingException e) {
                        Log.d("twilio-startup", e.getMessage());
                    }
                }
            }
        };
    }




    public void updateCartPreview(){
        // Setup shopping cart view
        ((TextView)fragmentView.findViewById(R.id.itemCountText)).setText(String.valueOf(cart.getNumberItemsInCart()));
        ((TextView)fragmentView.findViewById(R.id.subtotalText)).setText(format.format(cart.getPretax()));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        gridView.setNumColumns(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE ? 4 : 2);
        super.onConfigurationChanged(newConfig);
    }


    public Product[] products;

    public class ProductAdapter extends BaseAdapter {

        private Context mContext;
        public ProductAdapter(Context c){
            mContext = c;
        }

        @Override
        public int getCount() {
            return products.length;
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater i = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = (View) i.inflate(R.layout.product_layout, parent, false);
            }

            TextView p = (TextView) convertView.findViewById(R.id.price);
            ImageView i = (ImageView) convertView.findViewById(R.id.image);

            NumberFormat format = NumberFormat.getCurrencyInstance();
            p.setText(format.format(products[position].price));
            i.setImageResource(products[position].imageResource);

            return convertView;
        }
    }

    public class ShoppingCart{
        ArrayList<ShoppingCartItem> items;
        String firstName;
        String lastName;
        String phoneNumber;
        String email;
        String shippingAddress;
        String country;
        String city;
        String zip;

        private float taxRate;
        private float baseShipping;
        private float  perItemShipping;

        public ShoppingCart(float taxRate, float baseShipping, float perItemShipping){
            items = new ArrayList<ShoppingCartItem>();
            this.taxRate =taxRate;
            this.baseShipping = baseShipping;
            this.perItemShipping = perItemShipping;
        }


        public void clearCart(){
            items.clear();
        }

        public void addItemToCart(ShoppingCartItem item){
            items.add(item);
        }


        public int getNumberItemsInCart() {
            int numberOfItems = 0;
            for (ShoppingCartItem item : items) {
                numberOfItems = numberOfItems + item.quantity;
            }
            return numberOfItems;
        }

        public float getPretax(){
            float total = 0;
            for (ShoppingCartItem item :items){
                total = total + item.quantity*item.product.price;
            }
            return total;
        }

        public float getTax(){
            return getPretax()*taxRate+1;
        }

        public float getShipping(){
            return baseShipping + getNumberItemsInCart()*perItemShipping;
        }

        public float getCartTotal(){
            return getPretax()+getTax()+getShipping();
        }

        public Dialog cartDialog;
        ListView list;
        ShoppingCartAdapter adapter;
        public void makeShoppingCartDialog(){
            final AlertDialog.Builder cartBuilder = new AlertDialog.Builder(getActivity());
            LayoutInflater cartFactory = LayoutInflater.from(getActivity());
            final View view = cartFactory.inflate(R.layout.shopping_cart_layout, null);
            cartBuilder.setView(view);

            adapter = new ShoppingCartAdapter(container.getContext(),items);

            // Set up the item grid

            list = (ListView) view.findViewById(R.id.shoppingCartList);
            list.post(new Runnable() {
                public void run() {
                    list.setAdapter(adapter);
                    Log.d("shopping", "adapter set");
                }
            });


            Button checkout = (Button) view.findViewById(R.id.checkoutButton);
            checkout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    makeCheckoutDialog();
                }
            });

            Button back = (Button) view.findViewById(R.id.backButton);
            back.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cartDialog.dismiss();
                }
            });

            cartDialog = cartBuilder.create();
            cartDialog.show();
            cartDialog.getWindow().setLayout((int) (displayMetrics.widthPixels * 0.85f), (int) (displayMetrics.heightPixels * 0.85f)); //Controlling width and height.
        }

        ProgressDialog progress;
        String currentPhoneNumber;

        public void submitToDatabase(){
            Log.d("shopping", "trying to submit order...");
            currentPhoneNumber = "+" + phoneNumber.replaceAll("[\\s\\-()]", "");
            if (currentPhoneNumber.length() == 12) {

                progress = new ProgressDialog(getActivity());

                progress.setMessage("Submitting your order... ");
                progress.show();

                String contactListing = firstName + "," + lastName + "," + phoneNumber + "," + email + "," + shippingAddress + "," + city + "," + country + "," + zip + ";\n";
                String productListings = "";
                for (ShoppingCartItem item : items) {
                    productListings = productListings + item.toString();
                }
                String orderPricing = "Pretax: " + String.valueOf(getPretax()) + ", Tax: " + String.valueOf(getTax()) + ", Shipping: " + String.valueOf(getShipping()) + ", Total: " + String.valueOf(getCartTotal()) + ";\n";

                String allOrderData = contactListing + productListings + orderPricing;
                Log.d("shopping", allOrderData);
                String filename = firstName + lastName + "_" + Calendar.getInstance().getTime().toString() + ".txt";
                StorageReference thisFile = ordersThisDevice.child(filename);

                UploadTask uploadTask = thisFile.putBytes(allOrderData.getBytes());
                uploadTask.addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception exception) {
                        Log.d("shopping", exception.toString());
                        Log.d("shopping", "order upload failed");
                    }
                }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        Log.d("picture", "upload succeeded getting URL");
                        taskSnapshot.getMetadata().getReference().getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                            @Override
                            public void onSuccess(Uri publicURL) {
                                progress.dismiss();
                                checkoutDialog.dismiss();
                                cartDialog.dismiss();
                                cart = new ShoppingCart(taxRate, baseShipping, perItemShipping);
                                updateCartPreview();
                                Log.d("picture", "URL Succeeded: " + publicURL.toString());
                                sendTwilioSMS(currentPhoneNumber, "Your order for "+ format.format(getCartTotal()) +" with Beauty and the Bolt has been recorded. You'll recieve an invoice for payment within 24 hours.");
                            }
                        });
                    }
                });
            }
            else{
                Toast toast = Toast.makeText(getActivity(), "Please enter a valid phone number with country and area codes", Toast.LENGTH_SHORT);
                toast.setGravity(Gravity.CENTER, 0, 0);
                toast.show();
            }
        }

        public Dialog checkoutDialog;

        public void makeCheckoutDialog(){
            final AlertDialog.Builder checkoutBuilder = new AlertDialog.Builder(getActivity());
            LayoutInflater checkoutFactory = LayoutInflater.from(getActivity());
            final View view = checkoutFactory.inflate(R.layout.product_purchase_layout, null);
            checkoutBuilder.setView(view);
            ((TextView)view.findViewById(R.id.preTaxValue)).setText(format.format(getPretax()));
            ((TextView)view.findViewById(R.id.taxValue)).setText(format.format(getTax()));
            ((TextView)view.findViewById(R.id.shippingValue)).setText(format.format(getShipping()));
            ((TextView)view.findViewById(R.id.totalValue)).setText(format.format(getCartTotal()));

            Button submitOrder = view.findViewById(R.id.submitOrderButton);
            submitOrder.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.d("shopping", "Submitting order...");
                    firstName = ((EditText)checkoutDialog.findViewById(R.id.orderFirstName)).getText().toString();
                    lastName = ((EditText)checkoutDialog.findViewById(R.id.orderLastName)).getText().toString();
                    phoneNumber = ((EditText)checkoutDialog.findViewById(R.id.orderPhoneNumber)).getText().toString();
                    email = ((EditText)checkoutDialog.findViewById(R.id.orderEmail)).getText().toString();
                    shippingAddress = ((EditText)checkoutDialog.findViewById(R.id.orderShippingAddress)).getText().toString();
                    country = ((EditText)checkoutDialog.findViewById(R.id.orderCountry)).getText().toString();
                    city = ((EditText)checkoutDialog.findViewById(R.id.orderCity)).getText().toString();
                    zip = ((EditText)checkoutDialog.findViewById(R.id.orderZipcode)).getText().toString();
                    submitToDatabase();
                }
            });

            Button cancelOrder = view.findViewById(R.id.cancelCheckoutButton);
            cancelOrder.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    checkoutDialog.dismiss();
                }
            });

            checkoutDialog = checkoutBuilder.create();
            checkoutDialog.show();
            checkoutDialog.getWindow().setLayout((int) (displayMetrics.widthPixels * 0.85f), (int) (displayMetrics.heightPixels * 0.85f)); //Controlling width and height.

        }

        public class ShoppingCartAdapter extends BaseAdapter {

            private Context mContext;
            private ArrayList<ShoppingCartItem> items;
            public ShoppingCartAdapter(Context c, ArrayList<ShoppingCartItem> items){
                mContext = c;
                this.items = items;
            }

            @Override
            public int getCount() {
                Log.d("shopping", "getting count: "+items.size());
                return items.size();
            }

            @Override
            public Object getItem(int position) {
                return null;
            }

            @Override
            public long getItemId(int position) {
                return 0;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                Log.d("shopping", "trying make view...");

                if (convertView == null) {
                    LayoutInflater i = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    convertView = (View) i.inflate(R.layout.cart_item_layout, parent, false);
                }

                TextView nameField = (TextView) convertView.findViewById(R.id.item_name);
                TextView optionField = (TextView) convertView.findViewById(R.id.item_options);
                TextView priceField = (TextView) convertView.findViewById(R.id.item_price);
                TextView quantityField = (TextView) convertView.findViewById(R.id.item_quantity);

                NumberFormat format = NumberFormat.getCurrencyInstance();
                nameField.setText(items.get(position).product.name);
                optionField.setText(items.get(position).option);
                priceField.setText(format.format(items.get(position).product.price));
                quantityField.setText(String.valueOf(items.get(position).quantity));
                Log.d("shopping", "made view...");

                return convertView;
            }
        }
    }

    public class ShoppingCartItem{
        Product product;
        int quantity;
        String option;

        public ShoppingCartItem(Product product, int quantity, String option){
            this.product = product;
            this.quantity = quantity;
            this.option = option;
        }

        public String toString(){
            String output = product.name+","+product.price+","+option+","+String.valueOf(quantity)+";\n";
            return output;
        }
    }


    public class Product {
        public Product thisProduct;
        public int imageResource;
        public String name;
        public float price;
        public String description;
        public String[] options;

        public Product(int imResID, String n, float p, String[] optns, String des){
            thisProduct = this;
            imageResource = imResID;
            name = n;
            price = p;
            description = des;
            options = optns;
        }

        AlertDialog detailDialog;

        public boolean makeDetailDialog(Context c){
            final AlertDialog.Builder detailBuilder = new AlertDialog.Builder(c);
            LayoutInflater detailFactory = LayoutInflater.from(c);
            final View view = detailFactory.inflate(R.layout.product_detail_layout, null);
            ((ImageView) view.findViewById(R.id.dialog_imageview)).setImageResource(imageResource);
            ((TextView) view.findViewById(R.id.dialog_product_name)).setText(name);
            ((TextView) view.findViewById(R.id.dialog_product_price)).setText(format.format(price));
            ((TextView) view.findViewById(R.id.dialog_product_description)).setText(description);

            Spinner dropdown = view.findViewById(R.id.spinner1);

            if(options != null) {
                // Setup options list
                ArrayAdapter<String> adapter = new ArrayAdapter<>(getActivity(), R.layout.support_simple_spinner_dropdown_item, options);
                dropdown.setAdapter(adapter);
            }
            else{
                dropdown.setVisibility(View.INVISIBLE);
            }

            ((Button) view.findViewById(R.id.orderButton)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String quant = ((EditText)detailDialog.findViewById(R.id.orderQuantity)).getText().toString();
                    int quantity = -1;

                    try{
                        quantity = Integer.valueOf(quant);
                    }
                    catch (Exception e){

                        Toast toast = Toast.makeText(getActivity(), "You must select a valid quantity", Toast.LENGTH_SHORT);
                        toast.setGravity(Gravity.CENTER, 0, 0);
                        toast.show();
                    }
                    if(quantity != -1) {
                        if (quantity == 0) {
                            Toast toast = Toast.makeText(getActivity(), "You must select a quantity greater than 0", Toast.LENGTH_SHORT);
                            toast.setGravity(Gravity.CENTER, 0, 0);
                            toast.show();
                        } else {
                            String option = "";
                            if (options != null) {
                                Spinner dropdown = view.findViewById(R.id.spinner1);
                                option = options[dropdown.getSelectedItemPosition()];
                            }

                            Log.d("shoppingCart", "Added " + quantity + " " + option + " " + name);

                            cart.addItemToCart(new ShoppingCartItem(thisProduct, quantity, option));
                            detailDialog.dismiss();
                            updateCartPreview();
                        }
                    }
                }
            });

            detailBuilder.setView(view);
            detailBuilder.setNeutralButton("Back", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dlg, int sumthin) {
                    detailDialog.dismiss();
                }
            });

            detailDialog = detailBuilder.create();
            detailDialog.show();
            detailDialog.getWindow().setLayout((int) (displayMetrics.widthPixels * 0.85f), (int) (displayMetrics.heightPixels * 0.85f)); //Controlling width and height.
            return true;
        }

        AlertDialog purchaseDialog;
//        public boolean MakePurchaseDialog(Context c){
//            final AlertDialog.Builder purchaseBuilder = new AlertDialog.Builder(c);
//            LayoutInflater purchaseFactory = LayoutInflater.from(c);
//            final View view = purchaseFactory.inflate(R.layout.product_purchase_layout, null);
//            ((ImageView) view.findViewById(R.id.purchase_imageview)).setImageResource(imageResource);
//            ((TextView) view.findViewById(R.id.purchase_product_name)).setText(name);
//            NumberFormat format = NumberFormat.getCurrencyInstance();
//            ((TextView) view.findViewById(R.id.purchase_product_price)).setText(format.format(price));
//
//            ((Button) view.findViewById(R.id.cancelButton)).setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    purchaseDialog.dismiss();
//                }
//            });
//
//            purchaseBuilder.setView(view);
//            purchaseDialog = purchaseBuilder.create();
//            purchaseDialog.show();
//            purchaseDialog.getWindow().setLayout((int) (displayMetrics.widthPixels * 0.85f), (int) (displayMetrics.heightPixels * 0.85f)); //Controlling width and height.
//            return true;
//        }
    }
}
