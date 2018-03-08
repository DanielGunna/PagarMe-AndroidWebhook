package br.gunna.pagarmewebhook.pagarme;

import android.text.TextUtils;
import android.util.Base64;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Created by Gunna on 08/03/2018.
 */

public class PagarMeWebhook {
    private PagarMeRequest mRequest;
    private PagarMeApi mApiService;
    private static PagarMeWebhook sInstance;
    private String mKey;

    private PagarMeWebhook(String key) {
        mRequest = new PagarMeRequest();
        mApiService = PagarMeService.getServiceInstance();
        mKey = key;
    }


    public static void initialize(String key) {
        sInstance = new PagarMeWebhook(key);
    }


    public static PagarMeWebhook getsInstance() {
        if (sInstance == null)
            throw new RuntimeException("You must initialize calling PagarMeWebhook.initialize(apiKey) !!!");
        return sInstance;
    }

    public PagarMeWebhook holderName(String value) {
        mRequest.setHolderName(value);
        return this;
    }

    public PagarMeWebhook number(String value) {
        mRequest.setNumber(value);
        return this;
    }


    public PagarMeWebhook expirationDate(String value) {
        mRequest.setExpirationDate(value);
        return this;
    }


    public PagarMeWebhook cvv(String value) {
        mRequest.setCvv(value);
        return this;
    }

    public PagarMeWebhook telephone(String value) {
        mRequest.setTelephone(value);
        return this;
    }


    public PagarMeWebhook ddd(String value) {
        mRequest.setDdd(value);
        return this;
    }


    public PagarMeWebhook brand(String value) {
        mRequest.setBrand(value);
        return this;
    }


    public void generateCardHash(Listener listener) {
        if (!TextUtils.isEmpty(mKey)) {
            if (checkFieldsRequest(listener))
                generateKeyHash(listener);
        } else
            listener.onError(new RuntimeException("You must provide a valid non-empty PagarMe api key !! "));
    }

    private boolean checkFieldsRequest(Listener listener) {
        return true;
    }


    private void generateKeyHash(final Listener listener) {
        getPublicKey(new Callback<PagarMeResponse>() {
            @Override
            public void onResponse(Call<PagarMeResponse> call, Response<PagarMeResponse> response) {
                if (response.code() == 200) {
                    try {
                        final String cardHash = buildCardHash(response.body());
                        listener.onSuccess(cardHash);
                    } catch (Exception e) {
                        listener.onError(e);
                    }
                } else {
                    listener.onError(new Exception(new Throwable("PagarMeService:/Error generating card hash: " + response.code() + " " + response.message())));
                }
            }

            @Override
            public void onFailure(Call<PagarMeResponse> call, Throwable t) {
                listener.onError(new Exception(t));
            }
        });
    }


    private void getPublicKey(Callback<PagarMeResponse> callbackApi) {
        mApiService.getKeyHash(mKey).enqueue(callbackApi);
    }

    private String buildCardHash(PagarMeResponse publicKeyResponse)
            throws Exception {
        final long publicKeyId = publicKeyResponse.getId();
        final String publicKey = publicKeyResponse.getPublicKey();
        final String cardData = String.format("card_number=%s"
                        + "&card_holder_name=%s"
                        + "&card_expiration_date=%s"
                        + "&card_cvv=%s"
                        + "&brand=%s",
                mRequest.getNumber(), mRequest.getHolderName(), mRequest.getExpirationDate(),
                mRequest.getCvv(), mRequest.getBrand());
        final String encryptedData = encrypt(cardData, publicKey);
        return String.format("%s_%s", publicKeyId, encryptedData);
    }


    private String encrypt(String plain, String publicKey) throws Exception {
        final String pubKeyPEM = publicKey.replace("-----BEGIN PUBLIC KEY-----\n", "")
                .replace("-----END PUBLIC KEY-----", "");
        final byte[] keyBytes = Base64.decode(pubKeyPEM.getBytes("UTF-8"), Base64.DEFAULT);
        final X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        final KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        final PublicKey key = keyFactory.generatePublic(spec);
        final Cipher cipher = Cipher.getInstance("RSA/NONE/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        final byte[] encryptedBytes = cipher.doFinal(plain.getBytes());
        return Base64.encodeToString(encryptedBytes, Base64.DEFAULT);
    }

    public interface Listener {
        void onSuccess(String cardHash);

        void onError(Exception e);
    }
}
