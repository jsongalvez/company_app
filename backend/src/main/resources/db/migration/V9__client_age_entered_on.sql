-- The age is typed once; remember the day it was typed so the shown age goes up each year.
ALTER TABLE client ADD COLUMN age_entered_on DATE NOT NULL DEFAULT CURRENT_DATE;
