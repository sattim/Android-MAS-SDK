/*
 * Copyright (c) 2016 CA. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license.  See the LICENSE file for details.
 *
 */

package com.ca.mas.core.test.datasource;

import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.test.AndroidTestCase;

import com.ca.mas.core.datasource.DataConverter;
import com.ca.mas.core.datasource.DataSource;
import com.ca.mas.core.datasource.DataSourceFactory;
import com.ca.mas.core.datasource.KeystoreDataSource;
import com.ca.mas.core.datasource.StringDataConverter;

import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class KeystoreDataSourceTest {

    private final String KEY = "KEY";
    private final String KEY2 = "KEY2";
    private final String VALUE = "VALUE";
    private final String VALUE2 = "VALUE2";

    @After
    public void after() {
        DataSource<String, String> d = DataSourceFactory.getStorage(InstrumentationRegistry.getTargetContext(),
                KeystoreDataSource.class, null, null);
        d.remove(KEY);
        d.remove(KEY2);
    }

    @Test
    public void testReadWriteString() {
        DataSource<String, String> d = DataSourceFactory.getStorage(InstrumentationRegistry.getTargetContext(),
                KeystoreDataSource.class, null, new StringDataConverter());
        d.put(KEY, VALUE);
        assertEquals(VALUE, d.get(KEY));
    }

    @Test
    public void testReadWriteByteArray() {
        DataSource<String, byte[]> d = DataSourceFactory.getStorage(InstrumentationRegistry.getTargetContext(),
                KeystoreDataSource.class, null, null);
        d.put(KEY, VALUE.getBytes());
        assertEquals(VALUE, new String(d.get(KEY)));
    }

    @Test
    public void testReadWriteGeneric() {
        DataSource<String, Object> d = DataSourceFactory.getStorage(InstrumentationRegistry.getTargetContext(),
                KeystoreDataSource.class, null, new DataConverter() {
            @Override
            public Object convert(Object key, byte[] value) {
                if (key.equals(KEY)) {
                    return new String(value);
                } else {
                    return value;
                }
            }
        });
        d.put(KEY, VALUE);
        d.put(KEY2, VALUE2.getBytes());
        assertEquals(VALUE, d.get(KEY));
        assertEquals(VALUE2, new String((byte[]) d.get(KEY2)));
    }

    @Test
    public void testGetKeys() {
        DataSource<String, Object> d = DataSourceFactory.getStorage(InstrumentationRegistry.getTargetContext(),
                KeystoreDataSource.class, null, null);
        d.put(KEY, VALUE);
        d.put(KEY2, VALUE2.getBytes());
        assertTrue(d.getKeys(null).contains(KEY));
        assertTrue(d.getKeys(null).contains(KEY2));
    }

    @Test
    public void testRemove() {
        DataSource<String, Object> d = DataSourceFactory.getStorage(InstrumentationRegistry.getTargetContext(),
                KeystoreDataSource.class, null, null);
        d.put(KEY, VALUE);
        d.remove(KEY);
        assertNull(d.get(KEY));
    }

    @Ignore("Need to lock the phone to test")
    @Test
    public void testKeyStoreUnlock() {
        DataSource<String, Object> d = DataSourceFactory.getStorage(InstrumentationRegistry.getTargetContext(),
                KeystoreDataSource.class, null, null);
        assertFalse(d.isReady());
    }








}
