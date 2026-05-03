package com.booknest.bookservice.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.booknest.bookservice.entity.Book;

import jakarta.persistence.LockModeType;

/**
 * Repository for managing book archives.
 * Supports specialized full-text searching and secure locking for inventory updates.
 */
@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

	List<Book> findByTitleContainingIgnoreCase(String title);

	List<Book> findByAuthorContainingIgnoreCase(String author);

	Page<Book> findByGenreIgnoreCase(String genre, Pageable pageable);

	Optional<Book> findByIsbn(String isbn);

	List<Book> findByPriceBetween(Double min, Double max);

	// Executes a high-performance full-text search across book metadata
	@Query(value = "SELECT * FROM books WHERE MATCH(title, author, genre, description) AGAINST (CONCAT(:keyword, '*') IN BOOLEAN MODE)", countQuery = "SELECT COUNT(*) FROM books WHERE MATCH(title, author, genre, description) AGAINST (CONCAT(:keyword, '*') IN BOOLEAN MODE)", nativeQuery = true)
	Page<Book> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

	List<Book> findByRatingGreaterThanEqual(Double rating);
	
	List<Book> findByIsFeaturedTrue();
	
	@Query("SELECT b FROM Book b WHERE " +
	       "(:keyword IS NULL OR LOWER(b.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(b.author) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(b.isbn) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(b.genre) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
	       "(:genre IS NULL OR LOWER(b.genre) LIKE LOWER(CONCAT('%', :genre, '%'))) AND " +
	       "(:minPrice IS NULL OR b.price >= :minPrice) AND " +
	       "(:maxPrice IS NULL OR b.price <= :maxPrice) AND " +
	       "(:minRating IS NULL OR b.rating >= :minRating)")
	// Retrieves a filtered list of books based on dynamic search criteria
	Page<Book> findFilteredBooks(
			@Param("keyword") String keyword, 
			@Param("genre") String genre, 
			@Param("minPrice") Double minPrice, 
			@Param("maxPrice") Double maxPrice, 
			@Param("minRating") Double minRating, 
			Pageable pageable);

	// Fetches a volume with an exclusive lock for sensitive transaction updates
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT b FROM Book b WHERE b.bookId = :id")
	Optional<Book> findByIdForUpdate(@Param("id") Long id);
}
