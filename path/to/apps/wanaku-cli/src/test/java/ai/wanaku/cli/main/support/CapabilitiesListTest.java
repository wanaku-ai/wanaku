/**
 * Test class for capabilities list.
 */
public class CapabilitiesListTest {
    /**
     * Test that the label filter is correctly applied.
     */
    @Test
    public void testLabelFilter() {
        // Arrange
        CapabilitiesService capabilitiesService = mock(CapabilitiesService.class);
        List<Capability> capabilities = Arrays.asList(
                new Capability("capability1", Arrays.asList(new Label("label1"))),
                new Capability("capability2", Arrays.asList(new Label("label2")))
        );
        when(capabilitiesService.fetchCapabilities()).thenReturn(capabilities);

        // Act
        List<Capability> result = CapabilitiesList.doCall(capabilitiesService, "label1");

        // Assert
        assertEquals(1, result.size());
        assertEquals("capability1", result.get(0).getName());
    }
}