CREATE TABLE MESSAGE
(
    ID          BIGINT AUTO_INCREMENT PRIMARY KEY,
    NAME        VARCHAR(255) NOT NULL,
    SYNC        BIT NULL,
    CREATE_DATE DATETIME,
    UPDATE_DATE DATETIME
)
    ENGINE=INNODB DEFAULT CHARSET=UTF8;