package uk.org.taverna.httpclient.jarcache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.SystemDefaultHttpClient;
import org.apache.http.impl.client.cache.CachingHttpClient;
import org.junit.After;
import org.junit.Test;

public class TestJarCache {
	
	@Test
	public void cacheHit() throws Exception {
		JarCacheStorage storage = new JarCacheStorage();		
		HttpClient httpClient = new CachingHttpClient(
				new SystemDefaultHttpClient(), storage, storage.getCacheConfig());
		HttpGet get = new HttpGet("http://nonexisting.example.com/");		
		HttpResponse resp = httpClient.execute(get);		
		
		assertEquals("text/html", resp.getEntity().getContentType().getValue());
		String str = IOUtils.toString(resp.getEntity().getContent(), "UTF-8");
		assertTrue(str.contains("Hello World"));
	}
	
	
	@Test(expected=IOException.class)
	public void cacheMiss() throws Exception {
		JarCacheStorage storage = new JarCacheStorage();		
		HttpClient httpClient = new CachingHttpClient(
				new SystemDefaultHttpClient(), storage, storage.getCacheConfig());
		HttpGet get = new HttpGet("http://nonexisting.example.com/notfound");
		// Should throw an IOException as the DNS name 
		// nonexisting.example.com does not exist
		HttpResponse resp = httpClient.execute(get);
	}
	

	@Test
	public void doubleLoad() throws Exception {
		JarCacheStorage storage = new JarCacheStorage();		
		HttpClient httpClient = new CachingHttpClient(
				new SystemDefaultHttpClient(), storage, storage.getCacheConfig());
		HttpGet get = new HttpGet("http://nonexisting.example.com/");		
		HttpResponse resp = httpClient.execute(get);		
		resp = httpClient.execute(get);	
		// Ensure second load through the cached jarcache list works
		assertEquals("text/html", resp.getEntity().getContentType().getValue());
	}
	
	@Test
	public void customClassPath() throws Exception {
		URL nestedJar = getClass().getResource("/nested.jar");
		ClassLoader cl = new URLClassLoader(new URL[]{ nestedJar } );
		JarCacheStorage storage = new JarCacheStorage(cl);
		
		HttpClient httpClient = new CachingHttpClient(
				new SystemDefaultHttpClient(), storage, storage.getCacheConfig());
		HttpGet get = new HttpGet("http://nonexisting.example.com/nested/");		
		HttpResponse resp = httpClient.execute(get);
		
		assertEquals("text/plain", resp.getEntity().getContentType().getValue());
		String str = IOUtils.toString(resp.getEntity().getContent(), "UTF-8");
		assertEquals("Hello World!", str.trim());
	}
	
	@Test
	public void contextClassLoader() throws Exception {
		URL nestedJar = getClass().getResource("/nested.jar");
		ClassLoader cl = new URLClassLoader(new URL[]{ nestedJar } );
		
		Thread.currentThread().setContextClassLoader(cl);
		JarCacheStorage storage = new JarCacheStorage();
		
		HttpClient httpClient = new CachingHttpClient(
				new SystemDefaultHttpClient(), storage, storage.getCacheConfig());
		HttpGet get = new HttpGet("http://nonexisting.example.com/nested/");		
		HttpResponse resp = httpClient.execute(get);
		
		assertEquals("text/plain", resp.getEntity().getContentType().getValue());
		String str = IOUtils.toString(resp.getEntity().getContent(), "UTF-8");
		assertEquals("Hello World!", str.trim());
	}

	@After
	public void setContextClassLoader() {
		Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
	}
	
	@Test
	public void systemClassLoader() throws Exception {
		URL nestedJar = getClass().getResource("/nested.jar");
		ClassLoader cl = new URLClassLoader(new URL[]{ nestedJar } );
		JarCacheStorage storage = new JarCacheStorage(null);
		
		HttpClient httpClient = new CachingHttpClient(
				new SystemDefaultHttpClient(), storage, storage.getCacheConfig());
		HttpGet get = new HttpGet("http://nonexisting.example.com/");		
		HttpResponse resp = httpClient.execute(get);		
		assertEquals("text/html", resp.getEntity().getContentType().getValue());
	}

	
}
