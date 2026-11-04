package objectstorage

import (
	"context"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"

	"kanban-board/internal/config"

	"github.com/minio/minio-go/v7"
	"github.com/minio/minio-go/v7/pkg/credentials"
)

type Object struct {
	Reader      io.ReadCloser
	Size        int64
	ContentType string
}

type Storage interface {
	Put(context.Context, string, io.Reader, int64, string) error
	Get(context.Context, string) (*Object, error)
	Delete(context.Context, string) error
}

func New(ctx context.Context, cfg config.MinIO) (Storage, error) {
	if !cfg.Enabled {
		return &localStorage{root: "data"}, nil
	}
	client, err := minio.New(cfg.Endpoint, &minio.Options{
		Creds:  credentials.NewStaticV4(cfg.AccessKey, cfg.SecretKey, ""),
		Secure: cfg.UseSSL,
	})
	if err != nil {
		return nil, fmt.Errorf("create minio client: %w", err)
	}
	exists, err := client.BucketExists(ctx, cfg.Bucket)
	if err != nil {
		return nil, fmt.Errorf("check minio bucket: %w", err)
	}
	if !exists {
		if err := client.MakeBucket(ctx, cfg.Bucket, minio.MakeBucketOptions{}); err != nil {
			return nil, fmt.Errorf("create minio bucket: %w", err)
		}
	}
	return &minioStorage{client: client, bucket: cfg.Bucket}, nil
}

type minioStorage struct {
	client *minio.Client
	bucket string
}

func (s *minioStorage) Put(ctx context.Context, key string, reader io.Reader, size int64, contentType string) error {
	_, err := s.client.PutObject(ctx, s.bucket, filepath.ToSlash(key), reader, size, minio.PutObjectOptions{ContentType: contentType})
	return err
}

func (s *minioStorage) Get(ctx context.Context, key string) (*Object, error) {
	object, err := s.client.GetObject(ctx, s.bucket, filepath.ToSlash(key), minio.GetObjectOptions{})
	if err != nil {
		return nil, err
	}
	info, err := object.Stat()
	if err != nil {
		object.Close()
		return nil, err
	}
	return &Object{Reader: object, Size: info.Size, ContentType: info.ContentType}, nil
}

func (s *minioStorage) Delete(ctx context.Context, key string) error {
	return s.client.RemoveObject(ctx, s.bucket, filepath.ToSlash(key), minio.RemoveObjectOptions{})
}

type localStorage struct {
	root string
}

func (s *localStorage) path(key string) string {
	cleaned := filepath.ToSlash(filepath.Clean(key))
	if strings.HasPrefix(cleaned, "data/") {
		return key
	}
	return filepath.Join(s.root, filepath.FromSlash(key))
}

func (s *localStorage) Put(_ context.Context, key string, reader io.Reader, _ int64, _ string) error {
	path := s.path(key)
	if err := os.MkdirAll(filepath.Dir(path), 0750); err != nil {
		return err
	}
	file, err := os.OpenFile(path, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, 0640)
	if err != nil {
		return err
	}
	_, copyErr := io.Copy(file, reader)
	closeErr := file.Close()
	if copyErr != nil {
		_ = os.Remove(path)
		return copyErr
	}
	return closeErr
}

func (s *localStorage) Get(_ context.Context, key string) (*Object, error) {
	file, err := os.Open(s.path(key))
	if err != nil {
		return nil, err
	}
	info, err := file.Stat()
	if err != nil {
		file.Close()
		return nil, err
	}
	return &Object{Reader: file, Size: info.Size()}, nil
}

func (s *localStorage) Delete(_ context.Context, key string) error {
	err := os.Remove(s.path(key))
	if os.IsNotExist(err) {
		return nil
	}
	return err
}
