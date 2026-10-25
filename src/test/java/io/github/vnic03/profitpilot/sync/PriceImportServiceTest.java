package io.github.vnic03.profitpilot.sync;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.github.vnic03.profitpilot.api.alphavantage.ApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PriceImportServiceTest {

  private ApiClient client;
  private ImportPause pause;

  @BeforeEach
  void setUp() {
    client = mock(ApiClient.class);
    pause = mock(ImportPause.class);
  }

  private PriceImportService service(String symbols) {
    return new PriceImportService(client, pause, mock(ImportRunStore.class), 15000, symbols);
  }

  @Test
  void disabledProviderDoesNotStartImportsOrWait() {
    when(client.isImportEnabled()).thenReturn(false);

    var report = service("IBM,AAPL").runConfiguredImport();

    assertEquals(PriceImportService.RunStatus.DISABLED, report.status());

    verify(client).isImportEnabled();
    verifyNoMoreInteractions(client);
    verifyNoInteractions(pause);
  }

  @Test
  void failedStockDoesNotPreventNextImport() throws Exception {
    when(client.isImportEnabled()).thenReturn(true);

    when(client.syncDailyPrices("IBM")).thenThrow(new IllegalStateException("Provider failed."));

    when(client.syncDailyPrices("AAPL")).thenReturn(ApiClient.SyncResult.IMPORTED);

    var report = service("IBM,AAPL").runConfiguredImport();

    assertEquals(PriceImportService.RunStatus.COMPLETED, report.status());
    assertEquals(1, report.imported());
    assertEquals(1, report.failed());
    assertEquals(0, report.skipped());

    var order = inOrder(client, pause);

    order.verify(client).isImportEnabled();
    order.verify(client).syncDailyPrices("IBM");
    order.verify(pause).await(15000);
    order.verify(client).syncDailyPrices("AAPL");
  }

  @Test
  void invalidConfigurationIsRejectedBeforeAnyImport() {
    when(client.isImportEnabled()).thenReturn(true);

    assertThrows(
        IllegalArgumentException.class, () -> service("IBM,UNKNOWN").runConfiguredImport());

    verify(client).isImportEnabled();
    verifyNoMoreInteractions(client);
    verifyNoInteractions(pause);
  }

  @Test
  void duplicateSymbolsAreImportedOnlyOnce() throws Exception {
    when(client.isImportEnabled()).thenReturn(true);

    when(client.syncDailyPrices("IBM")).thenReturn(ApiClient.SyncResult.IMPORTED);

    var report = service("ibm, IBM").runConfiguredImport();

    assertEquals(1, report.imported());
    verify(client, times(1)).syncDailyPrices("IBM");
    verifyNoInteractions(pause);
  }

  @Test
  void interruptionStopsRemainingImportsAndReleasesRunLock() throws Exception {

    when(client.isImportEnabled()).thenReturn(true);

    when(client.syncDailyPrices("IBM")).thenReturn(ApiClient.SyncResult.IMPORTED);

    doThrow(new InterruptedException("Shutdown")).when(pause).await(15000);

    var imports = service("IBM,AAPL");

    try {
      var report = imports.runConfiguredImport();

      assertEquals(PriceImportService.RunStatus.INTERRUPTED, report.status());
      assertTrue(Thread.currentThread().isInterrupted());
      verify(client, never()).syncDailyPrices("AAPL");
    } finally {
      Thread.interrupted();
    }

    when(client.isImportEnabled()).thenReturn(false);

    assertEquals(PriceImportService.RunStatus.DISABLED, imports.runConfiguredImport().status());
  }

  @Test
  void overlappingRunIsRejected() throws Exception {
    when(client.isImportEnabled()).thenReturn(true);

    var imports = service("IBM");

    when(client.syncDailyPrices("IBM"))
        .thenAnswer(
            invocation -> {
              var overlapping = imports.runConfiguredImport();

              assertEquals(PriceImportService.RunStatus.ALREADY_RUNNING, overlapping.status());

              return ApiClient.SyncResult.IMPORTED;
            });

    assertEquals(1, imports.runConfiguredImport().imported());
    verify(client, times(1)).syncDailyPrices("IBM");
  }

  @Test
  void waitingForProviderDataDoesNotPreventNextImport() throws Exception {
    when(client.isImportEnabled()).thenReturn(true);

    when(client.syncDailyPrices("IBM")).thenReturn(ApiClient.SyncResult.WAITING_FOR_DATA);

    when(client.syncDailyPrices("AAPL")).thenReturn(ApiClient.SyncResult.IMPORTED);

    var report = service("IBM,AAPL").runConfiguredImport();

    assertEquals(PriceImportService.RunStatus.COMPLETED, report.status());

    assertEquals(1, report.imported());
    assertEquals(1, report.waiting());
    assertEquals(0, report.failed());
    assertEquals(0, report.skipped());

    var order = inOrder(client, pause);

    order.verify(client).isImportEnabled();
    order.verify(client).syncDailyPrices("IBM");
    order.verify(pause).await(15000);
    order.verify(client).syncDailyPrices("AAPL");
  }
}
