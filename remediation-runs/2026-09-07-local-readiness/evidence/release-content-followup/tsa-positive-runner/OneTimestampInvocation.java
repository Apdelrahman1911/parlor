import java.net.HttpURLConnection;
import sun.security.tools.jarsigner.Main;

/** Campaign-only network policy around the pinned JDK's actual jarsigner entry point. */
class OneTimestampInvocation {
    public static void main(String[] args) throws Exception {
        if (Runtime.version().feature() != 21
                || !"false".equals(System.getProperty("sun.net.http.retryPost"))) {
            throw new IllegalArgumentException("JDK21 and response-side POST retry disabled required");
        }
        int endpoints = 0;
        for (int index = 0; index < args.length; index++) {
            if (args[index].equals("-tsa")) {
                if (++endpoints != 1 || index + 1 == args.length
                        || !args[++index].equals("https://timestamp.sectigo.com")) {
                    throw new IllegalArgumentException("Only the authorized HTTPS endpoint is permitted");
                }
            }
        }
        if (endpoints != 1) throw new IllegalArgumentException("Exactly one TSA endpoint is required");
        // HttpTimestamper creates new connections after this call and never
        // changes their inherited redirect policy. maxRedirects=0 alone is NOT
        // sufficient: the JDK reconnects before checking that loop limit.
        HttpURLConnection.setFollowRedirects(false);
        if (HttpURLConnection.getFollowRedirects()) throw new AssertionError("Redirect policy changed");
        // This is one invocation, not a claim of exactly one outbound POST:
        // JDK21 can internally retry a failed write of the same synthetic query.
        Main.main(args);
    }
}
