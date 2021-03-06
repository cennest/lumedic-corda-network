
CREATE TABLE IF NOT EXISTS Test_Har_States (
    ID BIGINT AUTO_INCREMENT PRIMARY KEY,
    HarID       VARCHAR(100) NOT NULL, 
    Provider    VARCHAR(100) NOT NULL,
    Payer       VARCHAR(100) NOT NULL,
    Branch      VARCHAR(100) NOT NULL,
    Automated   BIT NOT NULL,
    EventDate   TIMESTAMP NOT NULL,
    Status      VARCHAR(100) NOT NULL 
 )



SELECT 
    COALESCE(MAX(OPEN), 0) OPEN, 
    COALESCE(MAX(PENDING), 0) PENDING, 
    COALESCE(MAX(CONFIRMED), 0) CONFIRMED, 
    COALESCE(MAX(DENIED), 0) DENIED
    FROM ( 
     SELECT 
        CASE WHEN STATUS = 'OPEN' THEN COUNT(*)  ELSE 0 END "OPEN", 
        CASE WHEN STATUS = 'PENDING'  THEN COUNT(*)   ELSE 0 END "PENDING", 
        CASE WHEN STATUS = 'CONFIRMED'  THEN COUNT(*) ELSE 0 END "CONFIRMED", 
        CASE WHEN STATUS = 'DENIED'  THEN COUNT(*)   ELSE 0 END "DENIED" 
      FROM TEST_HAR_STATES GROUP BY STATUS
    );


SELECT  H1.HarID, H1.PROVIDER,  H1.PAYER, H1.BRANCH,  H1.AUTOMATED, 
        YEAR(H1.EVENTDATE) YEAR, MONTH(H1.EVENTDATE) MONTH, DATEDIFF(SECOND, H1.EVENTDATE , H2.EVENTDATE) PROCESSTIME  
FROM TEST_HAR_STATES H1
INNER JOIN TEST_HAR_STATES H2 ON H2.HARID = H1.HARID AND H2.STATUS = 'CONFIRMED'
WHERE H1.STATUS = 'OPEN'
AND H1.EVENTDATE BETWEEN '2019-01-01' AND '2019-01-31'
AND H2.EVENTDATE BETWEEN '2019-01-01' AND '2019-01-31'
LIMIT 10 OFFSET 0


SELECT 
    PROVIDER,
    PAYER,
    BRANCH,
    YEAR,
    MONTH,
    SUM(AUTO) AUTO,
    SUM(AUTO_TIME) AUTO_TIME,
    ROUND(SUM(AUTO_TIME) / SUM(AUTO), 3) AVG_AUTO_TIME,
    SUM(MANUAL) MANUAL,
    SUM(MANUAL_TIME) MANUAL_TIME,
    ROUND(SUM(MANUAL_TIME) / SUM(MANUAL), 3) AVG_MANUAL_TIME,
    'OPEN-CONFIRMED' TYPE 
    FROM ( 
     SELECT
        DS.PROVIDER, DS.PAYER, DS.BRANCH, DS.YEAR, DS.MONTH,
        CASE WHEN AUTOMATED = 1 THEN COUNT(*) ELSE 0 END AUTO, 
        CASE WHEN AUTOMATED = 0 THEN COUNT(*) ELSE 0 END MANUAL,
        CASE WHEN AUTOMATED = 1 THEN SUM(DS.PROCESSTIME) ELSE 0 END AUTO_TIME, 
        CASE WHEN AUTOMATED = 0 THEN SUM(DS.PROCESSTIME) ELSE 0 END MANUAL_TIME
      FROM (SELECT H1.HarID, H1.PROVIDER,  H1.PAYER, H1.BRANCH,  H1.AUTOMATED, 
                   YEAR(H1.EVENTDATE) YEAR, MONTH(H1.EVENTDATE) MONTH, DATEDIFF(SECOND, H1.EVENTDATE , H2.EVENTDATE) PROCESSTIME  
            FROM TEST_HAR_STATES H1
            INNER JOIN TEST_HAR_STATES H2 ON H2.HARID = H1.HARID 
            WHERE H1.STATUS = 'OPEN' AND H2.STATUS = 'CONFIRMED'
            AND (LENGTH(COALESCE('','')) = 0 OR (H1.PROVIDER = '' AND H1.PROVIDER = H2.PROVIDER))
            AND (LENGTH(COALESCE('','')) = 0 OR (H1.PAYER = '' AND H1.PAYER = H2.PAYER))
            AND (LENGTH(COALESCE('','')) = 0 OR (H1.BRANCH = '' AND H1.BRANCH = H2.BRANCH))
            AND H1.EVENTDATE BETWEEN '2019-01-01' AND '2019-01-31'
            AND H2.EVENTDATE BETWEEN '2019-01-01' AND '2019-01-31'
            ) DS
    GROUP BY DS.PROVIDER, DS.PAYER, DS.BRANCH, DS.AUTOMATED, DS.YEAR, DS.MONTH) MS
    GROUP BY MS.PROVIDER, MS.PAYER, MS.BRANCH, MS.MONTH, MS.YEAR