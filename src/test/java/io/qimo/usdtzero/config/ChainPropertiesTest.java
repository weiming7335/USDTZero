package io.qimo.usdtzero.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChainPropertiesTest {

    @Test
    void testDefaultSmartContractAddresses() {
        ChainProperties chainProperties = new ChainProperties();
        chainProperties.validate();
        
        assertEquals("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t", chainProperties.getTrc20SmartContract());
        assertEquals("Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB", chainProperties.getSolSmartContract());
    }

    @Test
    void testEmptySmartContractAddresses() {
        ChainProperties chainProperties = new ChainProperties();
        chainProperties.setTrc20SmartContract("");
        chainProperties.setSolSmartContract("");
        chainProperties.validate();
        
        assertEquals("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t", chainProperties.getTrc20SmartContract());
        assertEquals("Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB", chainProperties.getSolSmartContract());
    }

    @Test
    void testDefaultRpcAddresses() {
        ChainProperties chainProperties = new ChainProperties();
        chainProperties.validate();
        
        assertEquals("https://api.trongrid.io", chainProperties.getTrc20Rpc());
        assertEquals("https://api.mainnet-beta.solana.com", chainProperties.getSolRpc());
    }

    @Test
    void testEmptyRpcAddresses() {
        ChainProperties chainProperties = new ChainProperties();
        chainProperties.setTrc20Rpc("");
        chainProperties.setSolRpc("");
        chainProperties.validate();
        
        assertEquals("https://api.trongrid.io", chainProperties.getTrc20Rpc());
        assertEquals("https://api.mainnet-beta.solana.com", chainProperties.getSolRpc());
    }

    @Test
    void testNullRpcAddresses() {
        ChainProperties chainProperties = new ChainProperties();
        chainProperties.setTrc20Rpc(null);
        chainProperties.setSolRpc(null);
        chainProperties.validate();
        
        assertEquals("https://api.trongrid.io", chainProperties.getTrc20Rpc());
        assertEquals("https://api.mainnet-beta.solana.com", chainProperties.getSolRpc());
    }

    @Test
    void testCustomRpcAddresses() {
        ChainProperties chainProperties = new ChainProperties();
        chainProperties.setTrc20Rpc("https://custom-trc20-rpc.com");
        chainProperties.setSolRpc("https://custom-solana-rpc.com");
        chainProperties.validate();
        
        assertEquals("https://custom-trc20-rpc.com", chainProperties.getTrc20Rpc());
        assertEquals("https://custom-solana-rpc.com", chainProperties.getSolRpc());
    }

    @Test
    void testDefaultEnableValues() {
        ChainProperties chainProperties = new ChainProperties();
        chainProperties.validate();
        
        assertFalse(chainProperties.getTrc20Enable());
        assertFalse(chainProperties.getSolEnable());
    }

    @Test
    void testNullEnableValues() {
        ChainProperties chainProperties = new ChainProperties();
        chainProperties.setTrc20Enable(null);
        chainProperties.setSolEnable(null);
        chainProperties.validate();
        
        assertFalse(chainProperties.getTrc20Enable());
        assertFalse(chainProperties.getSolEnable());
    }


} 