package com.ucm.informatica.spread.Presenter;

import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentPagerAdapter;
import android.util.Log;

import com.ucm.informatica.spread.Contracts.AlertContract;
import com.ucm.informatica.spread.Contracts.PosterContract;
import com.ucm.informatica.spread.Data.IPFSService;
import com.ucm.informatica.spread.Data.LocalWallet;
import com.ucm.informatica.spread.Data.SmartContract;
import com.ucm.informatica.spread.Fragments.HistoricalFragment;
import com.ucm.informatica.spread.Fragments.HomeFragment;
import com.ucm.informatica.spread.Fragments.MapFragment;
import com.ucm.informatica.spread.Fragments.ProfileFragment;
import com.ucm.informatica.spread.Fragments.SettingsFragment;
import com.ucm.informatica.spread.Utils.Constants;
import com.ucm.informatica.spread.Utils.ViewPagerTab;
import com.ucm.informatica.spread.View.MainTabView;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jFactory;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.core.methods.response.Web3ClientVersion;
import org.web3j.protocol.http.HttpService;

import java.io.IOException;
import java.math.BigInteger;
import java.util.concurrent.ExecutionException;

import rx.Observer;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

import static android.app.Activity.RESULT_OK;
import static com.ucm.informatica.spread.Utils.Constants.Contract.CONTRACT_ADDRESS_ALERT;
import static com.ucm.informatica.spread.Utils.Constants.Contract.CONTRACT_ADDRESS_POSTER;
import static com.ucm.informatica.spread.Utils.Constants.REQUEST_IMAGE_POSTER_CAMERA;
import static com.ucm.informatica.spread.Utils.Constants.REQUEST_IMAGE_POSTER_GALLERY;

public class MainTabPresenter {

    private MainTabView mainTabView;
    private String walletPath;

    private LocalWallet localWallet;
    private Web3j web3j;

    private AlertContract alertContract;
    private PosterContract posterContract;
    private SmartContract smartContract;

    private IPFSService ipfsService;

    public MainTabPresenter(MainTabView mainTabView){
        this.mainTabView = mainTabView;
        this.ipfsService = new IPFSService(mainTabView);
    }

    public void start(){
        walletPath = mainTabView.getWalletFilePath();
        mainTabView.showLoading();

        mainTabView.initNotificationService();
        initEthConnection();
    }

    public Fragment getFragment(int position) {
        Fragment fragment;
        switch (position) {
            case 0:
                fragment = new HomeFragment();
                break;
            case 1:
                fragment = new ProfileFragment();
                break;
            case 2:
                fragment = new MapFragment();
                break;
            case 3:
                fragment = new HistoricalFragment();
                break;
            default:
                fragment = SettingsFragment.newInstance(getAddress(), mainTabView.getPasswordLocally(), getBalance());
                break;
        }
        return fragment;
    }

    private String getAddress(){
        return localWallet.getCredentials() != null ? localWallet.getCredentials().getAddress() : "No disponible";
    }

    private String getBalance() {
        EthGetBalance ethGetBalance = null;
        if(localWallet.getCredentials() != null) {
            try {
                ethGetBalance = web3j
                        .ethGetBalance(localWallet.getCredentials().getAddress(), DefaultBlockParameterName.LATEST)
                        .sendAsync()
                        .get();
            } catch (InterruptedException | ExecutionException | NullPointerException e) {
                Log.e("WALLET BALANCE",e.getMessage());
            }
        }
        return ethGetBalance != null ? ethGetBalance.getBalance().toString() + " ETH" : "No disponible";

    }

    private void initEthConnection() {
        localWallet = new LocalWallet(mainTabView.getPasswordLocally());
        if(web3j == null) {
            web3j = Web3jFactory.build(new HttpService(Constants.INFURA_PATH + Constants.INFURA_PUBLIC_PROYECT_ADDRESS));
        }
        web3j.web3ClientVersion().observable()
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<Web3ClientVersion>() {
                    @Override
                    public void onCompleted() {
                            String filenameWallet = mainTabView.getFilenameWalletLocally();
                            if (filenameWallet != null && !filenameWallet.isEmpty()) {
                                localWallet.setCredentials(localWallet.loadWallet(walletPath,filenameWallet));
                                loadData();
                            }
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.e("ERROR WEB3 CONNECTION",e.getMessage());
                    }

                    @Override
                    public void onNext(Web3ClientVersion web3ClientVersion) {
                        Log.i("CONNECTED TO %s", web3ClientVersion.getWeb3ClientVersion());
                    }
                });
    }

    private void getAlertContract(){
        smartContract = new SmartContract(web3j, localWallet.getCredentials());
        alertContract = smartContract.loadAlertSmartContract(CONTRACT_ADDRESS_ALERT);
    }

    private void getPosterContract(){
        smartContract = new SmartContract(web3j, localWallet.getCredentials());
        posterContract = smartContract.loadPosterSmartContract(CONTRACT_ADDRESS_POSTER);
    }

    private void loadData() {
        if(alertContract == null) {
            getAlertContract();
        }
        alertContract.getAlertsCount().observable()
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    (countCoords) -> {
                        for(int i =0; i<countCoords.intValue(); i++){
                            alertContract.getAlertByIndex(BigInteger.valueOf(i)).observable()
                                    .subscribeOn(Schedulers.newThread())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe(
                                        (result) ->
                                                mainTabView.loadDataAlertSmartContract(
                                                        result.getValue1(),
                                                        result.getValue2(),
                                                        result.getValue3(),
                                                        result.getValue4(),
                                                        result.getValue5())
                                        ,
                                        (error) -> mainTabView.showErrorTransaction()
                                    );
                        }
                    },
                    (error) -> mainTabView.showErrorTransaction()
                );
        if(posterContract == null) {
            getPosterContract();
        }
        posterContract.getPostersCount().observable()
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    (countCoords) -> {
                        for(int i=0; i<countCoords.intValue(); i++){
                            posterContract.getPosterByIndex(BigInteger.valueOf(i)).observable()
                                    .subscribeOn(Schedulers.newThread())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe(
                                        (resultHash) -> ipfsService.getDataFromHash(resultHash,
                                                countCoords.intValue())
                                        ,
                                        (error) -> mainTabView.showErrorTransaction()
                                    );
                        }
                    },
                    (error) -> mainTabView.showErrorTransaction()
                );
    }

    public void onSaveDataAlert(String title, String description, String latitude, String longitude, String date){
        if(alertContract == null) {
            getAlertContract();
        }
        alertContract.addAlert(title,description,latitude,longitude, date).observable()
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        (result) -> mainTabView.showConfirmationTransaction()
                        ,
                        (error) -> mainTabView.showErrorTransaction()
                );
    }

    public void onSaveDataPoster(String posterJson){
        if(posterContract == null) {
            getPosterContract();
        }
        ipfsService.addStringGetHash(posterJson, posterContract);
    }

    public void manageOnActivityResult(int requestCode, int resultCode, Intent data,
                                       ContentResolver contentResolver,
                                       FragmentPagerAdapter fragmentPagerAdapter,
                                       ViewPagerTab fragmentViewPager) {
        Bundle extras;
        Bitmap imageBitmap;
        if(resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_IMAGE_POSTER_CAMERA:
                    extras = data.getExtras();
                    imageBitmap = (Bitmap) extras.get("data");
                    fragmentViewPager.setCurrentItem(2);
                    ((MapFragment)fragmentPagerAdapter.getItem(2)).renderContentWithPicture(imageBitmap);
                    break;
                case REQUEST_IMAGE_POSTER_GALLERY:
                    try {
                        imageBitmap = MediaStore.Images.Media.getBitmap(contentResolver, data.getData());
                        fragmentViewPager.setCurrentItem(2);
                        ((MapFragment)fragmentPagerAdapter.getItem(2)).renderContentWithPicture(imageBitmap);
                    } catch (IOException e) {
                        Log.e("PICTURE", e.getMessage());
                    }
                    break;
            }

        }
    }
}
