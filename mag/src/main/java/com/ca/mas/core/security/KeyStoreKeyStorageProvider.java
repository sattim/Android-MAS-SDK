/*
 * Copyright (c) 2016 CA. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license.  See the LICENSE file for details.
 *
 */

package com.ca.mas.core.security;

import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.Log;

import com.ca.mas.core.util.KeyUtils;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.InvalidParameterException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import static com.ca.mas.core.MAG.DEBUG;
import static com.ca.mas.core.MAG.TAG;

public abstract class KeyStoreKeyStorageProvider implements KeyStorageProvider {

    // For Android.M+, use the AndroidKeyStore
    // Otherwise, encrypt using RSA key with "RSA/ECB/PKCS1Padding"
    //    which actually doesn't implement ECB mode encryption.
    //    It should have been called "RSA/None/PKCS1Padding" as it can only be used to
    //    encrypt a single block of plaintext (The secret key)
    //    This may be naming mistake.

    protected DefaultKeySymmetricManager keyMgr;

    protected static final String ASYM_KEY_ALIAS = "ASYM_KEY";
    public static final String RSA_ECB_PKCS1_PADDING = "RSA/ECB/PKCS1PADDING";

    private Context context;

    /**
     * Default constructor generates a DefaultKeySymmetricManager
     *
     * @param ctx context
     */
    public KeyStoreKeyStorageProvider(@NonNull Context ctx) {
        context = ctx.getApplicationContext();

        // Symmetric Key Manager creates symmetric keys,
        //   stored inside AndroidKeyStore for Android.M+
        keyMgr = new DefaultKeySymmetricManager("AES", 256, true, false, -1);

    }

    /**
     * Retrieve the SecretKey from Storage
     * @param alias : The alias to find the Key
     * @return The SecretKey
     */
    @Override
    public SecretKey getKey(String alias)
    {
        // For Android.M+, if this key was created we'll find it here
        SecretKey secretKey = keyMgr.retrieveKey(alias);

        if (secretKey == null) {

            // check if the key is present locally
            byte encryptedSecretKey[] = getEncryptedSecretKey(alias);
            if (encryptedSecretKey != null) {
                try {
                    secretKey = decryptSecretKey(encryptedSecretKey);
                } catch (Exception unableToDecrypt) {
                    if (DEBUG) Log.e(TAG, "Error while decrypting SecretKey, deleting it", unableToDecrypt);

                    deleteSecretKeyLocally(alias);
                    encryptedSecretKey = null;
                }
            }

            // if still no key, generate one
            if (secretKey == null) {
                secretKey = keyMgr.generateKey(alias);

                // if this is Pre- Android.M, we need to store it locally
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    encryptedSecretKey = encryptSecretKey(secretKey);
                    storeSecretKeyLocally(alias, encryptedSecretKey);
                }
            }
            else {
                // if this is Android.M+, check if the operating system was upgraded
                //   and we can now store the SecretKey in the AndroidKeyStore
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    keyMgr.storeKey(alias, secretKey);
                    deleteSecretKeyLocally(alias);
                }
            }
        }

        return secretKey;
    }


    /**
     * Remove the key
     * @param alias the alias of the key to remove
     */
    @Override
    public boolean removeKey(String alias)
    {
        keyMgr.deleteKey(alias);
        deleteSecretKeyLocally(alias);
        return true;
    }


    /**
     * @param alias              The alias to store the key against.
     * @param encryptedSecretKey The encrypted secret key to store.
     * @return
     */
    abstract boolean storeSecretKeyLocally(String alias, byte[] encryptedSecretKey);

    /**
     * @param alias The alias for the required secret key.
     * @return the encrypted bytes
     */
    abstract byte[] getEncryptedSecretKey(String alias);

    /**
     * Delete the secret key locally.
     *
     * @param alias
     * @return success / fail
     */
    abstract boolean deleteSecretKeyLocally(String alias);


    /**
     * This method encrypts a SecretKey using an RSA key.
     *   This is intended for Pre-Android.M, where the 
     *   SecretKey cannot be stored in the AndroidKeyStore
     *
     * @param secretKey    SecretKey to encrypt
     */
    protected byte[] encryptSecretKey(SecretKey secretKey)
    {
        try {
            PublicKey publicKey = KeyUtils.getRsaPublicKey(ASYM_KEY_ALIAS);
            if (publicKey == null) {
                KeyUtils.generateRsaPrivateKey(context, 2048,
                            ASYM_KEY_ALIAS, String.format("CN=%s, OU=%s", ASYM_KEY_ALIAS, "com.ca"),
                            false);
                publicKey = KeyUtils.getRsaPublicKey(ASYM_KEY_ALIAS);
            }

            Cipher cipher = Cipher.getInstance(RSA_ECB_PKCS1_PADDING);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            return cipher.doFinal(secretKey.getEncoded());

        } catch (InvalidKeyException | BadPaddingException | IllegalBlockSizeException
                | NoSuchPaddingException | NoSuchAlgorithmException | NoSuchProviderException
                | IOException | InvalidParameterException | KeyStoreException
                | InvalidAlgorithmParameterException | CertificateException
                | UnrecoverableKeyException e) {
            if (DEBUG) Log.e(TAG, "Error while encrypting SecretKey", e);
            throw new RuntimeException("Error while encrypting SecretKey", e);
        }
    }

    /**
     * This method decrypts a SecretKey using an RSA key.
     *   This is intended for Pre-Android.M, where the
     *   SecretKey cannot be stored in the AndroidKeyStore
     *
     * @param encryptedSecretKey  the encrypted bytes of the secret key
     */
    protected SecretKey decryptSecretKey(byte encryptedSecretKey[])
    {
        try {
            PrivateKey privateKey = KeyUtils.getRsaPrivateKey(ASYM_KEY_ALIAS);

            Cipher cipher = Cipher.getInstance(RSA_ECB_PKCS1_PADDING);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decryptedSecretkey = cipher.doFinal(encryptedSecretKey);
            return new SecretKeySpec(decryptedSecretkey, "AES");

        } catch (NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException
                | BadPaddingException | IllegalBlockSizeException
                | IOException | KeyStoreException | CertificateException
                | UnrecoverableKeyException e) {
            if (DEBUG) Log.e(TAG, "Error while decrypting SecretKey", e);
            throw new RuntimeException("Error while decrypting SecretKey", e);
        }


    }

    public Context getContext() {
        return context;
    }
}
