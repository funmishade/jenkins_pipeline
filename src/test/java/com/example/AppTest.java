import com.example.Application;
import org.junit.Test;
import static org.junit.Assert.*;

publoc class AppTest {
    
    @Test
    public void testApp() {
        Application myApp = new Application();
        String result = myApp.getStatus();
        assertEquals("OK", result);
    }
}
