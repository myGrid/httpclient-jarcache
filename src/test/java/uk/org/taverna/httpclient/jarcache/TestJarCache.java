package uk.org.taverna.httpclient.jarcache;

import static org.junit.Assert.assertTrue;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.SystemDefaultHttpClient;
import org.apache.http.impl.client.cache.CachingHttpClient;
import org.junit.Test;

public class TestJarCache {
	
	@Test
	public void cache() throws Exception {
		JarCacheStorage storage = new JarCacheStorage();		
		HttpClient httpClient = new CachingHttpClient(
				new SystemDefaultHttpClient(), storage, storage.getCacheConfig());
		HttpGet get = new HttpGet("http://nonexisting.example.com/");
		
		HttpResponse resp = httpClient.execute(get);		
		
//		assertEquals("text/html", resp.getEntity().getContentType().getValue());
		String str = IOUtils.toString(resp.getEntity().getContent(), "UTF-8");
		assertTrue(str.contains("Hello World"));
	}
}
