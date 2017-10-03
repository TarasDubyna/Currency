package com.example.taras.currency.Network;

import android.content.Context;
import android.support.annotation.NonNull;
import android.widget.Toast;

import com.example.taras.currency.DB.DatabaseHelper;
import com.example.taras.currency.Model.Currency;
import com.example.taras.currency.Model.Pair;
import com.example.taras.currency.StatisticActivity;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jakewharton.retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RequestRetrofit {

    private Retrofit retrofitFactory;
    private CurrencyAPIService currencyApi;
    private Throwable throwable = null;
    private Consumer<Throwable> errorHandler = new Consumer<Throwable>() {
        @Override
        public void accept(Throwable throwable) throws Exception {
            Toast.makeText(context, throwable.getMessage(), Toast.LENGTH_LONG).show();
        }
    };
    private Consumer<Currency.List> responseHandler;

    private Context context;
    private ArrayList<Pair> currenciesPair;
    private String currencyName;



    public RequestRetrofit(Context context) {
        this.context = context;
        this.initHandler();
        this.initRestClient();
    }

    public void setCurrenciesName(String currencyName){
        this.currencyName = currencyName;
    }



    private class CurrencyResponseHanlder implements Consumer<Currency.List> {

        public CurrencyResponseHanlder() {
        }

        @Override
        public void accept(Currency.List currencies) throws Exception {
            boolean endList = true;
            for (int i = 0; i < currenciesPair.size(); i++){
                if (currencies.get(0).getExchangeDate().equals(currenciesPair.get(i).getDate())){
                    currenciesPair.get(i).setCurrency(currencies.get(0));
                }
                if (currenciesPair.get(i).getCurrency() == null){
                    endList = false;
                }
            }
            if (endList){
                //createGraph(currenciesPair);
            }

        }
    }

    private void initHandler() {
        this.responseHandler = new CurrencyResponseHanlder();
    }
    private void initRestClient(){
        Gson gson = new GsonBuilder()
                .setLenient()

                .create();
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();

        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient.Builder httpClient = new OkHttpClient.Builder();
        httpClient.readTimeout(60, TimeUnit.SECONDS)
                .connectTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(logging);  // <-- this is the important line!


        this.retrofitFactory = new Retrofit.Builder()
                .baseUrl(CurrencyAPIService.BASE_URL)
                .client(httpClient.build())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build();
        this.currencyApi = retrofitFactory.create(CurrencyAPIService.class);
    }

    private ArrayList<Pair> generateDate(String fromDate, @NonNull String toDate, int rangeDays){
        ArrayList<Pair> outputPairList = new ArrayList<>();

        Calendar gcal = convertStringToCalenar(toDate);
        gcal.add(Calendar.DAY_OF_MONTH, 1);
        Date lastdate = gcal.getTime();

        if (fromDate == null){
            gcal.add(Calendar.DAY_OF_MONTH, -(rangeDays));
        } else {
            gcal = convertStringToCalenar(fromDate);
        }
        DatabaseHelper databaseHelper = new DatabaseHelper(context);
        do {
            Date d = gcal.getTime();
            if (databaseHelper.isDateInDatabase(convertDateToString(d))){
                outputPairList.add(new Pair(convertDateToString(d), null, databaseHelper.getCurrenciesByDate(convertDateToString(d))));
            } else {
                outputPairList.add(new Pair(convertDateToString(d), null, new ArrayList<Currency>()));
            }
            gcal.add(Calendar.DAY_OF_MONTH, 1);
        } while (gcal.getTime().before(lastdate));
        return outputPairList;
    }

    private Calendar convertStringToCalenar(String date) {
        Calendar calendar = Calendar.getInstance();
        try {
            calendar.setTime(new SimpleDateFormat("dd.MM.yyyy").parse(date));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return calendar;
    }
    private String convertDateToString(Date date){
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);

        String year = String.valueOf(calendar.get(Calendar.YEAR));
        String month = String.valueOf(calendar.get(Calendar.MONTH) + 1);
        String day = String.valueOf(calendar.get(Calendar.DAY_OF_MONTH));

        if (month.length() < 2){
            month = "0" + month;
        }
        if (day.length() < 2){
            day = "0" + day;
        }

        return new String(day + "." + month + "." + year);
    }
    String convertToUrlDate(String inputDate){
        String outputDate = null;
        try {
            java.text.SimpleDateFormat preDateFormat = new java.text.SimpleDateFormat("dd.MM.yyyy");
            java.util.Date date = preDateFormat.parse(inputDate);
            java.text.SimpleDateFormat postDateFormat = new java.text.SimpleDateFormat("yyyyMMdd");
            outputDate = postDateFormat.format(date);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return outputDate;
    }

    private void takeRequest(){
        List<Observable<Currency.List>> currencyObservables = new ArrayList<>();
        for (Pair pair : currenciesPair){
            if (pair.getCurrency() == null){
                currencyObservables.add(currencyApi.getCurrencyByDate(currencyName, convertToUrlDate(pair.getDate()), ""));
            }
        }
        if (currencyObservables.size()!=0){
            Observable.merge(currencyObservables)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(responseHandler, errorHandler);
        }
    }

    public void takeRequestRangeDays(String fromDate, String toDate){
        currenciesPair = new ArrayList<>();
        currenciesPair = generateDate(fromDate, toDate, Integer.parseInt(null));
        takeRequest();
    }

    public void takeRequestFromDate(String toDate, int rangeDays){
        currenciesPair = new ArrayList<>();
        currenciesPair = generateDate(null, toDate, rangeDays);
        takeRequest();


    }
}
