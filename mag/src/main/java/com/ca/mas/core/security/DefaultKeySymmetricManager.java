/*
 * Copyright (c) 2016 CA. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license.  See the LICENSE file for details.
 *
 */

package com.ca.mas.core.security;

import android.annotation.TargetApi;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProtection;
import android.util.Log;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import static android.security.keystore.KeyProperties.BLOCK_MODE_CBC;
import static android.security.keystore.KeyProperties.BLOCK_MODE_CTR;
import static android.security.keystore.KeyProperties.BLOCK_MODE_GCM;
import static android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE;
import static android.security.keystore.KeyProperties.PURPOSE_DECRYPT;
import static android.security.keystore.KeyProperties.PURPOSE_ENCRYPT;
import static com.ca.mas.core.MAG.DEBUG;
import static com.ca.mas.core.MAG.TAG;

/**
 * This interface is used to generate a SecretKey for encryption purposes.
 *     For Android.M+, the key will be created in the AndroidKeyStore
 *         and will remain there.
 *     For Pre-Android.M, the key will be created and returned, and
 *         a KeyStorageProvider must be used to protect the key.  If
 *         the user upgrades to Android.M+, the key will be moved
 *         to the AndroidKeyStore.
 */
public class DefaultKeySymmetricManager implements KeySymmetricManager {

    // the following parameters apply to all key generation operations
    private String mAlgorithm = "AES";
    private int mKeyLength = 256;;

    // the following apply to keys created or stored in Android.M+
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private boolean mInMemory = true;
    private boolean mUserAuthenticationRequired = false;
    private int mUserAuthenticationValiditySeconds = -1;


    /**
     * Constructor, uses least secure defaults
     */
    public DefaultKeySymmetricManager() {
    }

    /**
     * Constructor, allows for full selection of key properties
     *
     * @param algorithm AES or other Symmetric Key algorithm
     * @param keyLength default is 256
     * @param inMemory for Android.M+ the key will be created outside the AndroidKeyStore and then stored
     *                    inside.  The in-memory copy can be used without user authentication until the
     *                    app is closed, dereferenced, or variable is destroyed.
     * @param userAuthenticationRequired for Android.M+ require screen lock.  Note, if inMemory is true,
     *                    this applies only to versions extracted from the AndroidKeyStore.
     * @param userAuthenticationValiditySeconds sets the duration for which this key is authorized
     *                    to be used after the user is successfully authenticated.
     */
    public DefaultKeySymmetricManager(String algorithm, int keyLength,
                       boolean inMemory, boolean userAuthenticationRequired, int userAuthenticationValiditySeconds)
    {

        if (keyLength < 0) {
            if (DEBUG) Log.d(TAG, "key length is less than zero, assigning default, " + mKeyLength);
        } else
            mKeyLength = keyLength;

        if (algorithm != null && algorithm.trim().length() == 0) {
            if (DEBUG) Log.d(TAG, "Algorithm is either null or zero length, assigning default, " + mAlgorithm);
        } else
            mAlgorithm = algorithm;

        mInMemory = inMemory;
        mUserAuthenticationRequired = userAuthenticationRequired;
        mUserAuthenticationValiditySeconds = userAuthenticationValiditySeconds;
    }




    /**
     * Return a secretKey to be used for encryption.
     *   For Android.M+, the key will be protected by
     *   the AndroidKeyStore.  If pre-M, the SecretKey
     *   must be stored using a KeyStorageProvider.
     */
    @Override
    public SecretKey generateKey(String alias)  {
        SecretKey returnKey = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            if (mInMemory) {

                // generate the key outside AndroidKeyStore then store
                returnKey = generateKeyInMemory(alias);
                storeKeyAndroidM(alias, returnKey);

            } else {

                // generate the key inside the keystore
                returnKey = generateKeyInAndroidKeyStore(alias);

            }

        } else {
            returnKey = generateKeyInMemory(alias);
        }
        return returnKey;
    }


    /**
     * Return a secretKey to be used for encryption.
     *   For Android.M+, the key will be protected by
     *   the AndroidKeyStore.  If pre-M, the SecretKey
     *   must be stored using a KeyStorageProvider.
     */
    private SecretKey generateKeyInMemory(String alias) {

        try {
            javax.crypto.KeyGenerator kg = javax.crypto.KeyGenerator.getInstance(mAlgorithm);
            kg.init(mKeyLength);
            return kg.generateKey();
        } catch (Exception x) {
            if (DEBUG) Log.e(TAG, "Error generateKeyInMemory", x);
            throw new RuntimeException("Error generateKeyInMemory", x);
        }
    }


    /**
     * For Android.m+ only, Return a secretKey to be used for encryption.
     *   For Android.M+, the key will be protected by
     *   the AndroidKeyStore.  If pre-M, the SecretKey
     *   must be stored using a KeyStorageProvider.
     */
    @TargetApi(Build.VERSION_CODES.M)
    private SecretKey generateKeyInAndroidKeyStore(String alias) {

        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                    mAlgorithm, ANDROID_KEY_STORE);
            KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(alias, PURPOSE_ENCRYPT | PURPOSE_DECRYPT)
                    .setKeySize(mKeyLength)
                    .setBlockModes(BLOCK_MODE_CBC, BLOCK_MODE_CTR, BLOCK_MODE_GCM)
                    .setEncryptionPaddings(ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .setUserAuthenticationRequired(mUserAuthenticationRequired);

            if (mUserAuthenticationRequired && mUserAuthenticationValiditySeconds > 0)
                builder.setUserAuthenticationValidityDurationSeconds(mUserAuthenticationValiditySeconds);

            KeyGenParameterSpec keyGenSpec = builder.build();
            keyGenerator.init(keyGenSpec);

            SecretKey key = keyGenerator.generateKey();
            return key;

        } catch (Exception x) {
            if (DEBUG) Log.e(TAG, "Error generateKeyInAndroidKeyStore", x);
            throw new RuntimeException("Error generateKeyInAndroidKeyStore", x);
        }
    }


    /**
     * Retrieve the key from the AndroidKeyStore
     * @return for Android Pre-M, this will return null
     *    otherwise it will try to find the key in the AndroidKeyStore
     */
    @Override
    public SecretKey retrieveKey(String alias)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
           // get the key from the AndroidKeyStore
           return retrieveKeyAndroidM(alias);
        }

        // if Android Pre-M, the key will not be in the AndroidKeyStore
        return null;
    }



    /**
     * Retrieve the key from the AndroidKeyStore
     * @return the SecretKey, or null if not present
     */
    @TargetApi(Build.VERSION_CODES.M)
    public SecretKey retrieveKeyAndroidM(String alias)
    {
        java.security.KeyStore ks;
        try {
            ks = java.security.KeyStore.getInstance(ANDROID_KEY_STORE);
        } catch (KeyStoreException e) {
            if (DEBUG) Log.e(TAG, "Error while instantiating Android KeyStore instance", e);
            throw new RuntimeException("Error while instantiating Android KeyStore instance", e);
        }
        try {
            ks.load(null);
        } catch (IOException | NoSuchAlgorithmException | java.security.cert.CertificateException e) {
            if (DEBUG) Log.e(TAG, "Error while loading Android KeyStore instance", e);
            throw new RuntimeException("Error while loading Android KeyStore instance", e);
        }
        SecretKey sk;
        try {
            java.security.KeyStore.SecretKeyEntry entry = (java.security.KeyStore.SecretKeyEntry) ks.getEntry(alias, null);
            if (entry == null)
                return null;
            sk = entry.getSecretKey();
        } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableEntryException | NullPointerException e) {
            if (DEBUG) Log.e(TAG, "Error while getting entry from Android KeyStore", e);
            throw new RuntimeException("Error while getting entry from Android KeyStore", e);
        }
        return sk;
    }

    /** 
     * Stores a SecretKey in the AndroidKeyStore.
     *   This is only useful when the phone is upgraded
     *   to Android.M, where an existing key was stored
     *   outside of the AndroidKeyStore.
     */
    @Override
    public void storeKey(String alias, SecretKey secretKey)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
           // store it in AndroidKeyStore
           storeKeyAndroidM(alias, secretKey);
        }
    }


    /**
     * Stores a SecretKey in the AndroidKeyStore.
     *   This is only useful when the phone is upgraded
     *   to Android.M, where an existing key was stored
     *   outside of the AndroidKeyStore.
     */
    @TargetApi(Build.VERSION_CODES.M)
    private boolean storeKeyAndroidM(String alias, SecretKey key)
    {

        java.security.KeyStore ks;
        try {
            ks = java.security.KeyStore.getInstance(ANDROID_KEY_STORE);
            ks.load(null);
        } catch (KeyStoreException | java.security.cert.CertificateException | NoSuchAlgorithmException | IOException e) {
            if (DEBUG) Log.e(TAG, "Error instantiating Android keyStore");
            throw new RuntimeException("Error instantiating Android keyStore", e);
        }

        KeyProtection.Builder builder = new KeyProtection.Builder(PURPOSE_ENCRYPT | PURPOSE_DECRYPT)
                        .setBlockModes(BLOCK_MODE_CBC, BLOCK_MODE_CTR, BLOCK_MODE_GCM)
                        .setEncryptionPaddings(ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .setUserAuthenticationRequired(mUserAuthenticationRequired);

        if (mUserAuthenticationRequired && mUserAuthenticationValiditySeconds > 0)
            builder.setUserAuthenticationValidityDurationSeconds(mUserAuthenticationValiditySeconds);

        KeyProtection kp = builder.build();

        try {
            ks.setEntry(alias, new KeyStore.SecretKeyEntry(key), kp);
            return true;
        } catch (KeyStoreException e) {
            if (DEBUG) Log.e(TAG, "Error setting entry into Android keyStore");
            throw new RuntimeException("Error setting entry into Android keyStore", e);
        }
    }


    /**
     * This method deletes the given key from AndroidKeyStore
     *
     * @param alias
     */
    @Override
    public void deleteKey(String alias) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // remove it from AndroidKeyStore
            deleteKeyAndroidM(alias);
        }
    }


    /**
     * This method deletes the given key from AndroidKeyStore
     *
     * @param alias
     */
    @TargetApi(Build.VERSION_CODES.M)
    private void deleteKeyAndroidM(String alias) {
        KeyStore ks;
        try {
            ks = KeyStore.getInstance(ANDROID_KEY_STORE);
        } catch (KeyStoreException e) {
            if (DEBUG) Log.e(TAG, "Error instantiating Android keyStore");
            throw new RuntimeException("Error instantiating Android keyStore", e);
        }
        try {
            ks.load(null);
        } catch (IOException | NoSuchAlgorithmException | java.security.cert.CertificateException e) {
            if (DEBUG) Log.e(TAG, "Error loading Android keyStore");
            throw new RuntimeException("Error loading Android keyStore", e);
        }
        try {
            ks.deleteEntry(alias);
        } catch (KeyStoreException e) {
            if (DEBUG) Log.e(TAG, "Error deleting Android keyStore");
            throw new RuntimeException("Error deleting Android keyStore", e);
        }
    }

}
