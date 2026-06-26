package com.team04.mopl.content.storage.exception;

public class FileStorageException extends RuntimeException {
	public FileStorageException(String message) {
		super(message);
	}

	public FileStorageException(String message, Throwable ex) {
		super(message, ex);
	}
}
