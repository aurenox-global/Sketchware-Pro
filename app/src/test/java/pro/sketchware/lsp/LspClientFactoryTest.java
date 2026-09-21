package pro.sketchware.lsp;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class LspClientFactoryTest {

    @Test
    public void createDefault_withoutContext_returnsClientInstance() {
        LspClient client = LspClientFactory.createDefault();
        assertNotNull(client);
    }
}
