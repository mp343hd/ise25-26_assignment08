package de.seuhd.campuscoffee.domain.implementation;

import de.seuhd.campuscoffee.domain.configuration.ApprovalConfiguration;
import de.seuhd.campuscoffee.domain.exceptions.NotFoundException;
import de.seuhd.campuscoffee.domain.exceptions.ValidationException;
import de.seuhd.campuscoffee.domain.model.objects.Pos;
import de.seuhd.campuscoffee.domain.model.objects.Review;
import de.seuhd.campuscoffee.domain.model.objects.User;
import de.seuhd.campuscoffee.domain.ports.data.PosDataService;
import de.seuhd.campuscoffee.domain.ports.data.ReviewDataService;
import de.seuhd.campuscoffee.domain.ports.data.UserDataService;
import de.seuhd.campuscoffee.domain.tests.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Objects;

import static de.seuhd.campuscoffee.domain.tests.TestFixtures.getApprovalConfiguration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit and integration tests for the operations related to reviews.
 */
@ExtendWith(MockitoExtension.class)
public class ReviewServiceTest {
    private final ApprovalConfiguration approvalConfiguration = getApprovalConfiguration();

    @Mock
    private ReviewDataService reviewDataService;

    @Mock
    private UserDataService userDataService;

    @Mock
    private PosDataService posDataService;

    private ReviewServiceImpl reviewService;

    @BeforeEach
    void beforeEach() {
        reviewService = new ReviewServiceImpl(
                reviewDataService, userDataService, posDataService, approvalConfiguration
        );
    }

    @Test
    void approvalFailsIfUserIsAuthor() {
        // given
        Review review = TestFixtures.getReviewFixtures().getFirst();
        assertNotNull(review.author().id());
        when(userDataService.getById(review.author().id())).thenReturn(review.author());
        assertNotNull(review.id());
        when(reviewDataService.getById(review.id())).thenReturn(review);

        // when, then
        assertThrows(ValidationException.class, () -> reviewService.approve(review, review.author().getId()));
        verify(userDataService).getById(review.author().id());
        verify(reviewDataService).getById(review.getId());
    }

    @Test
    void approvalSuccessfulIfUserIsNotAuthor() {
        // given
        Review review = TestFixtures.getReviewFixtures().getFirst().toBuilder()
                .approvalCount(2)
                .approved(false)
                .build();
        User user = TestFixtures.getUserFixtures().getLast();
        assertNotNull(user.getId());
        when(userDataService.getById(user.getId())).thenReturn(user);
        assertNotNull(review.getId());
        when(reviewDataService.getById(review.getId())).thenReturn(review);
        when(reviewDataService.upsert(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Review approvedReview = reviewService.approve(review, user.getId());

        // then
        verify(userDataService).getById(user.getId());
        verify(reviewDataService).getById(review.getId());
        verify(reviewDataService).upsert(any(Review.class));
        assertThat(approvedReview.approvalCount()).isEqualTo(review.approvalCount() + 1);
        assertThat(approvedReview.approved()).isTrue();
    }

    @Test
    void getApprovedByPos() {
        // given
        Pos pos = TestFixtures.getPosFixtures().getFirst();
        assertNotNull(pos.getId());
        List<Review> reviews = TestFixtures.getReviewFixtures().stream()
                .map(review -> review.toBuilder()
                        .pos(pos)
                        .approvalCount(3)
                        .approved(true)
                        .build())
                .toList();
        when(posDataService.getById(pos.getId())).thenReturn(pos);
        when(reviewDataService.filter(pos, true)).thenReturn(reviews);

        // when
        List<Review> retrievedReviews = reviewService.filter(Objects.requireNonNull(pos.getId()), true);

        // then
        verify(posDataService).getById(pos.getId());
        verify(reviewDataService).filter(pos, true);
        assertThat(retrievedReviews).hasSize(reviews.size());
    }

    @Test
    void createReviewPosDoesNotExistException() {
        // given
        Review review = TestFixtures.getReviewFixtures().getFirst();
        assertNotNull(review.pos().getId());
        when(posDataService.getById(review.pos().getId())).thenThrow(
                new NotFoundException(review.pos().getClass(), review.pos().getId())
        );

        // when, then
        assertThrows(NotFoundException.class, () -> reviewService.upsert(review));
        verify(posDataService).getById(review.pos().getId());
    }

    @Test
    void userCannotCreateMoreThanOneReviewPerPos() {
        // given
        Review review = TestFixtures.getReviewFixtures().getFirst();
        Pos pos = review.pos();
        User author = review.author();
        assertNotNull(pos.getId());

        when(posDataService.getById(pos.getId())).thenReturn(pos);
        when(reviewDataService.filter(pos, author)).thenReturn(List.of(review));

        // when, then
        assertThrows(ValidationException.class, () -> reviewService.upsert(review)
        );
        verify(posDataService).getById(pos.getId());
        verify(reviewDataService).filter(pos, author);
    }

    @Test
    void testUpdateApprovalStatusForUnapprovedReview() {
        // given
        Review unapprovedReview = TestFixtures.getReviewFixtures().getFirst().toBuilder()
                .approvalCount(2)
                .approved(false)
                .build();
        
        // when
        Review updatedReview = reviewService.updateApprovalStatus(unapprovedReview);
        
        // then
        assertFalse(updatedReview.approved());
        
        // when
        Review approvedReview = unapprovedReview.toBuilder()
                .approvalCount(approvalConfiguration.minCount())
                .build();
        
        // when
        updatedReview = reviewService.updateApprovalStatus(approvedReview);
        
        // then
        assertTrue(updatedReview.approved());
    }

    @Test
    void approvalIncrementsCountButDoesNotApproveIfQuorumNotReached() {
        // given
        // Wir setzen den Count auf 0. Nach dem Aufruf ist er 1.
        // Wenn minCount > 1 ist (was er laut den anderen Tests ist), bleibt approved = false.
        Review review = TestFixtures.getReviewFixtures().getFirst().toBuilder()
                .approvalCount(0)
                .approved(false)
                .build();
        
        // Ein User, der nicht der Autor ist
        User user = TestFixtures.getUserFixtures().getLast();
        
        assertNotNull(user.getId());
        when(userDataService.getById(user.getId())).thenReturn(user);
        
        assertNotNull(review.getId());
        when(reviewDataService.getById(review.getId())).thenReturn(review);
        
        // Mock für upsert gibt einfach das Objekt zurück, das gespeichert werden soll
        when(reviewDataService.upsert(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Review resultReview = reviewService.approve(review, user.getId());

        // then
        verify(userDataService).getById(user.getId());
        verify(reviewDataService).getById(review.getId());
        verify(reviewDataService).upsert(any(Review.class));

        // 1. Der Count muss sich erhöht haben (0 -> 1)
        assertThat(resultReview.approvalCount()).isEqualTo(review.approvalCount() + 1);
        
        // 2. WICHTIG: Approved muss immer noch FALSE sein (das löst den Log-Eintrag im else-Zweig aus)
        assertThat(resultReview.approved()).isFalse();
    }
}
