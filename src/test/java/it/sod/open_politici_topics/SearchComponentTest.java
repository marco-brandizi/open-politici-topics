/*
 * 
 */
package it.sod.open_politici_topics;

import org.junit.Test;
import static java.lang.System.out;

/**
 * TODO: Comment me!
 *
 * <dl><dt>date</dt><dd>Feb 9, 2013</dd></dl>
 * @author Marco Brandizi
 *
 */
public class SearchComponentTest
{
	@Test
	public void basicTest ()
	{
		SearchComponent sc = new SearchComponent ();
		
		for ( String twitter: sc.findPoliticianTwitterAccount ( "lavoro, sport Cultura" ) )
			out.println ( twitter );
	}
}
