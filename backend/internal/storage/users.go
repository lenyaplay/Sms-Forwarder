package storage

import (
	"context"
	"database/sql"
	"errors"
	"strings"
)

var ErrLoginTaken = errors.New("login already taken")
var ErrUserNotFound = errors.New("user not found")

type User struct {
	ID           int64
	Login        string
	PasswordHash string
}

func CreateUser(ctx context.Context, db *sql.DB, login, passwordHash string) (User, error) {
	res, err := db.ExecContext(ctx,
		"INSERT INTO users (login, password_hash) VALUES (?, ?)", login, passwordHash)
	if err != nil {
		if isUniqueConstraintErr(err) {
			return User{}, ErrLoginTaken
		}
		return User{}, err
	}

	id, err := res.LastInsertId()
	if err != nil {
		return User{}, err
	}

	return User{ID: id, Login: login, PasswordHash: passwordHash}, nil
}

func GetUserByLogin(ctx context.Context, db *sql.DB, login string) (User, error) {
	var u User
	err := db.QueryRowContext(ctx,
		"SELECT id, login, password_hash FROM users WHERE login = ?", login).
		Scan(&u.ID, &u.Login, &u.PasswordHash)
	if errors.Is(err, sql.ErrNoRows) {
		return User{}, ErrUserNotFound
	}
	if err != nil {
		return User{}, err
	}
	return u, nil
}

func GetUserByID(ctx context.Context, db *sql.DB, id int64) (User, error) {
	var u User
	err := db.QueryRowContext(ctx,
		"SELECT id, login, password_hash FROM users WHERE id = ?", id).
		Scan(&u.ID, &u.Login, &u.PasswordHash)
	if errors.Is(err, sql.ErrNoRows) {
		return User{}, ErrUserNotFound
	}
	if err != nil {
		return User{}, err
	}
	return u, nil
}

func isUniqueConstraintErr(err error) bool {
	// modernc.org/sqlite reports constraint violations with this substring.
	return err != nil && strings.Contains(err.Error(), "UNIQUE constraint failed")
}
