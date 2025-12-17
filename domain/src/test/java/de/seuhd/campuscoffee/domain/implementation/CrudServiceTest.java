package de.seuhd.campuscoffee.domain.implementation;

import de.seuhd.campuscoffee.domain.exceptions.DuplicationException;
import de.seuhd.campuscoffee.domain.model.objects.DomainModel;
import de.seuhd.campuscoffee.domain.ports.data.CrudDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the abstract CrudServiceImpl class.
 * Uses a concrete inner class implementation to test the base logic.
 */
@ExtendWith(MockitoExtension.class)
class CrudServiceTest {

    @Mock
    private CrudDataService<TestDomainObject, Long> dataService;

    private TestCrudServiceImpl crudService;

    @BeforeEach
    void setUp() {
        // Wir initialisieren unsere konkrete Test-Implementierung mit dem Mock
        crudService = new TestCrudServiceImpl(dataService);
    }

    @Test
    void clearDelegatesToDataService() {
        // when
        crudService.clear();

        // then
        verify(dataService).clear();
    }

    @Test
    void getAllDelegatesToDataService() {
        // given
        List<TestDomainObject> expectedList = List.of(new TestDomainObject(1L), new TestDomainObject(2L));
        when(dataService.getAll()).thenReturn(expectedList);

        // when
        List<TestDomainObject> result = crudService.getAll();

        // then
        assertThat(result).hasSize(2).isEqualTo(expectedList);
        verify(dataService).getAll();
    }

    @Test
    void getByIdDelegatesToDataService() {
        // given
        Long id = 1L;
        TestDomainObject expectedObject = new TestDomainObject(id);
        when(dataService.getById(id)).thenReturn(expectedObject);

        // when
        TestDomainObject result = crudService.getById(id);

        // then
        assertThat(result).isEqualTo(expectedObject);
        verify(dataService).getById(id);
    }

    @Test
    void upsertCreatesNewEntityWhenIdIsNull() {
        // given
        TestDomainObject newObject = new TestDomainObject(null); // ID ist null -> Create
        TestDomainObject persistedObject = new TestDomainObject(1L); // Datenbank gibt Objekt mit ID zurück

        when(dataService.upsert(newObject)).thenReturn(persistedObject);

        // when
        TestDomainObject result = crudService.upsert(newObject);

        // then
        // Bei Create (id null) darf getById NICHT zur Prüfung aufgerufen werden
        verify(dataService, never()).getById(any());
        verify(dataService).upsert(newObject);
        assertThat(result).isEqualTo(persistedObject);
    }

    @Test
    void upsertUpdatesExistingEntityWhenIdIsNotNull() {
        // given
        Long id = 10L;
        TestDomainObject existingObject = new TestDomainObject(id);

        // Mocking: getById muss aufgerufen werden, um Existenz zu prüfen
        when(dataService.getById(id)).thenReturn(existingObject);
        when(dataService.upsert(existingObject)).thenReturn(existingObject);

        // when
        TestDomainObject result = crudService.upsert(existingObject);

        // then
        // Bei Update (id nicht null) MUSS getById aufgerufen werden (Logik aus CrudServiceImpl)
        verify(dataService).getById(id);
        verify(dataService).upsert(existingObject);
        assertThat(result).isEqualTo(existingObject);
    }

    @Test
    void upsertRethrowsDuplicationException() {
        // given
        TestDomainObject object = new TestDomainObject(null);
        String errorMsg = "TestDomainObject with id '999' already exists.";
        when(dataService.upsert(object)).thenThrow(new DuplicationException(TestDomainObject.class, "id", "999"));

        // when, then
        DuplicationException exception = assertThrows(DuplicationException.class, () -> crudService.upsert(object));
        assertThat(exception.getMessage()).isEqualTo(errorMsg);
        verify(dataService).upsert(object);
    }

    @Test
    void deleteDelegatesToDataService() {
        // given
        Long id = 1L;

        // when
        crudService.delete(id);

        // then
        verify(dataService).delete(id);
    }

    // --- Helper Classes for Testing ---

    /**
     * Ein einfaches Dummy-Objekt, das DomainModel implementiert.
     */
    static class TestDomainObject implements DomainModel<Long> {
        private final Long id;

        public TestDomainObject(Long id) {
            this.id = id;
        }

        @Override
        public Long getId() {
            return id;
        }
    }

    /**
     * Eine konkrete Implementierung der abstrakten Klasse, nur für diesen Test.
     */
    static class TestCrudServiceImpl extends CrudServiceImpl<TestDomainObject, Long> {
        private final CrudDataService<TestDomainObject, Long> dataService;

        public TestCrudServiceImpl(CrudDataService<TestDomainObject, Long> dataService) {
            super(TestDomainObject.class);
            this.dataService = dataService;
        }

        @Override
        protected CrudDataService<TestDomainObject, Long> dataService() {
            return dataService;
        }
    }
}