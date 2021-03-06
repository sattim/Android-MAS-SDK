/*
 * Copyright (c) 2016 CA. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license.  See the LICENSE file for details.
 *
 */

package com.ca.mas.foundation.auth;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.ca.mas.core.http.MAGHttpClient;
import com.ca.mas.core.http.MAGRequest;
import com.ca.mas.core.http.MAGResponse;
import com.ca.mas.core.http.MAGResponseBody;
import com.ca.mas.core.service.Provider;
import com.ca.mas.foundation.MASCallback;

import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;

/**
 * The MASAuthenticationProvider class is a representation of a single provider.
 */
public class MASAuthenticationProvider implements Parcelable {

    private Provider provider;

    protected MASAuthenticationProvider(Provider provider) {
        this.provider = provider;
    }

    public String getIdentifier() {
        return provider.getId();
    }

    public String getAuthenticationUrl() {
        return provider.getUrl();
    }

    public String getPollUrl() {
        return provider.getPollUrl();
    }

    public boolean isEnterprise() {
        return provider.getId().equalsIgnoreCase("enterprise");
    }

    public boolean isFacebook() {
        return provider.getId().equalsIgnoreCase("facebook");
    }

    public boolean isGoogle() {
        return provider.getId().equalsIgnoreCase("google");
    }

    public boolean isLinkedIn() {
        return provider.getId().equalsIgnoreCase("linkedin");
    }

    public boolean isSalesForce() {
        return provider.getId().equalsIgnoreCase("salesforce");
    }

    public boolean isProximityLogin() {
        return provider.getId().equalsIgnoreCase("qrcode");
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(this.provider, flags);
    }

    protected MASAuthenticationProvider(Parcel in) {
        this.provider = in.readParcelable(Provider.class.getClassLoader());
    }

    public static final Parcelable.Creator<MASAuthenticationProvider> CREATOR = new Parcelable.Creator<MASAuthenticationProvider>() {
        @Override
        public MASAuthenticationProvider createFromParcel(Parcel source) {
            return new MASAuthenticationProvider(source);
        }

        @Override
        public MASAuthenticationProvider[] newArray(int size) {
            return new MASAuthenticationProvider[size];
        }
    };

    private static class MagTask extends AsyncTask<Void, Void, MAGResponse> {
        Context context;
        MAGRequest request;
        MASCallback<Uri> callback;

        MagTask(Context context, MAGRequest request, MASCallback<Uri> callback) {
            this.context = context;
            this.request = request;
            this.callback = callback;
        }

        @Override
        protected MAGResponse<JSONObject> doInBackground(Void... params) {
            MAGHttpClient magHttpClient = new MAGHttpClient(context) {
                @Override
                protected void onConnectionObtained(HttpURLConnection connection) {
                    connection.setInstanceFollowRedirects(false);
                }
            };
            try {
                return magHttpClient.execute(request);
            } catch (IOException e) {
                Log.d("", e.getMessage());
            }

            return null;
        }

        @Override
        protected void onPostExecute(MAGResponse magResponse) {
            super.onPostExecute(magResponse);
            String location = null;
            Map<String, List<String>> headers = magResponse.getHeaders();
            location = headers.get("Location").get(0);
            callback.onSuccess(Uri.parse(location));
        }
    }

    public void getAuthConfiguration(Context context, MASAuthenticationProvider provider, MASCallback<Uri> callback) {
        MAGRequest request = new MAGRequest.MAGRequestBuilder(Uri.parse(provider.getAuthenticationUrl()))
                .get()
                .responseBody(MAGResponseBody.jsonBody())
                .build();

        MagTask magTask = new MagTask(context, request, callback);
        magTask.execute();
    }
}
