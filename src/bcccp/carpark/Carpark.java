package bcccp.carpark;

import java.util.ArrayList;
import java.util.List;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import bcccp.tickets.adhoc.IAdhocTicket;
import bcccp.tickets.adhoc.IAdhocTicketDAO;
import bcccp.tickets.season.ISeasonTicket;
import bcccp.tickets.season.ISeasonTicketDAO;
import java.sql.Time;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;



public class Carpark implements ICarpark {
	
	private List<ICarparkObserver> observers;
	private String carparkId;
	private int capacity;
        private int seasonCapacity;
	private int numberOfCarsParked;
	private IAdhocTicketDAO adhocTicketDAO;
	private ISeasonTicketDAO seasonTicketDAO;
        final float BH_RATE = 4; 
        final float OOH_RATE = 2;

        
	
    /**
     * Constructs a Carpark object with the name, capacity, and the data access objects passed to it.
     *
     * @param name
     * @param capacity
     * @param seasonCapacity
     * @param adhocTicketDAO
     * @param seasonTicketDAO
     */
    public Carpark(String name, int capacity, int seasonCapacity,
			IAdhocTicketDAO adhocTicketDAO, 
			ISeasonTicketDAO seasonTicketDAO) throws RuntimeException {
            //Sets the name of the carpark, throws exception at null value
            if (name != null){            
                this.carparkId = name;
            } else { throw new RuntimeException("Invalid carpark name");}
            
            //Sets the number of total available spaces in the carpark (inlcuding season spaces), throws exception at less than 0 value
            //Sets the season ticket capacity to between 0 and 10 percent of total capacity, throws runtimeException outside those values.
            //Special case implemented for 0 Season Ticket capacity, allowing construction while preventing division by 0.
            if (capacity > 0 && seasonCapacity > 0 && capacity / seasonCapacity * 10 >= 1 || 
                    capacity > 0 && seasonCapacity == 0){
                this.capacity = capacity;
                this.seasonCapacity = seasonCapacity;
            } else { throw new RuntimeException("Invalid number of parking spaces");}
            
            //Sets the carpark to be empty
            this.numberOfCarsParked = 0;
            
            //Assigns a SeasonTicketDAO to this carpark
            this.seasonTicketDAO = seasonTicketDAO;
            
            //Assigns an AdhocTicketDAO to this carpark
            this.adhocTicketDAO = adhocTicketDAO;
            
            //Initialises an arraylist of observers
            this.observers = new ArrayList<>();
        
	}

    /**
     * Registers the passed object (usually an entry controller) as an observer to this carpark.
     * @param observer
     */
    @Override
	public void register(ICarparkObserver observer) {
		if (!observers.contains(observer)) {
			observers.add(observer);
		}
		
	}

    /**
     * Removes the passed object (usually an entry controller) as an observer to this carpark.
     * @param observer
     */
    @Override
	public void deregister(ICarparkObserver observer) {
		if (observers.contains(observer)) {
			observers.remove(observer);
		}
		
	}
        
        private void log(String message) {
		System.out.println("Carpark : " + message);
	}

    /**
     *Returns the name of this carpark.
     * @return
     */
    @Override
	public String getName() {
		return this.carparkId;
	}

    /**
     * Returns true if the carpark cannot accept any more ad-hoc ticket customers.
     * @return
     */
    @Override
	public boolean isFull() {
            //Returns true if the number of number of adhoc ticket holders and 
            //the number of registered season tickets meets or exceeds the carpark's capacity.
            return (numberOfCarsParked + seasonTicketDAO.getNumberOfTickets() >= capacity);
        }



        //create and return new adhoc ticket
    @Override
	public IAdhocTicket issueAdhocTicket() {
            if (this.isFull()) {
                throw new RuntimeException("carpark is Full");
            }
            else {
                return adhocTicketDAO.createTicket(carparkId);
            }
	}

    /**
     * Also notifies all observers, allowing them to take an action if the carpark is full.
     * @param ticket
     */

    @Override
	public void recordAdhocTicketEntry() {
            numberOfCarsParked++;
	}



    @Override
	public IAdhocTicket getAdhocTicket(String barcode) { 
            //return adhocTicket object, or null if not found
		return adhocTicketDAO.findTicketByBarcode(barcode);

	}



    @Override
	public float calculateAdhocTicketCharge(long entryDateTime) {
            Date current = new Date();
            //calcCharge from given entryDateTime and current as payingTime
            return calcCharge(entryDateTime, current.getTime());
	}




	@Override
	public void recordAdhocTicketExit() {
            numberOfCarsParked--;
                for (int i = 0; i < observers.size(); i++){
                    observers.get(i).notifyCarparkEvent();
                }
	}

/**
 * registers season ticket with the carpark so that the season ticket may be used to access the carpark
 * @throws RuntimeException if the carpark the season ticket is associated with is not the same as the carpark name
 * @see bcccp.tickets.season.ISeasonTicketDAO#registerTicket(seasonTicket) 
 * @param seasonTicket 
 */
    @Override
	public void registerSeasonTicket(ISeasonTicket seasonTicket) {
            if (!seasonTicket.getCarparkId().equals(carparkId)){
            throw new RuntimeException("the carpark the season ticket is associated with is not the same as the carpark name");
        }
            if (capacity / (seasonCapacity * 10 + 1) >= 1 ){
               throw new RuntimeException("season ticket capacity will not be between 0 and 10 percent of total capacity");
            }
            if (seasonTicketDAO.findTicketById(seasonTicket.getId()) != null){
                throw new RuntimeException("season ticket is already registered");
            }
		seasonTicketDAO.registerTicket(seasonTicket);
	}

/**
 * deregisters season ticket
 * @see bcccp.tickets.season.ISeasonTicketDAO#deregisterTicket(seasonTicket) 
 * @param seasonTicket 
 */
    @Override
	public void deregisterSeasonTicket(ISeasonTicket seasonTicket) {
		seasonTicketDAO.deregisterTicket(seasonTicket);
		
	}


/**
 * Finds season ticket by Id and if it exists, is still valid and it is within business hours returns true else returns false 
 * @see bcccp.tickets.season.ISeasonTicketDAO#findTicketById(String ticketId)
 * @param ticketId
 * @return boolean
 */

    @Override
	public boolean isSeasonTicketValid(String ticketId) {
            ISeasonTicket seasonTicket = seasonTicketDAO.findTicketById(ticketId);  

            return ((seasonTicket != null) && (System.currentTimeMillis() <= seasonTicket.getEndValidPeriod()) &&
                   (System.currentTimeMillis() >= seasonTicket.getStartValidPeriod()) && (isBusinessHours()));
	}

/**
 * Finds season ticket by Id and then returns whether or not it is in use
 * @see bcccp.tickets.season.ISeasonTicketDAO#findTicketById(String ticketId) 
 * @param ticketId
 * @return boolean
 */
    @Override
	public boolean isSeasonTicketInUse(String ticketId) {
            ISeasonTicket seasonTicket = seasonTicketDAO.findTicketById(ticketId);
            return seasonTicket.getCurrentUsageRecord() != null;
        }


/**
 * causes a new usage record to be created and associated with a season ticket
 * @throws RuntimeException if the season ticket associated with ticketId does not exist, or is currently in use
 * @param ticketId 
 */
    @Override
	public void recordSeasonTicketEntry(String ticketId) {
            if (seasonTicketDAO.findTicketById(ticketId) == null){
            throw new RuntimeException("season ticket associated with ticketId does not exist");
        }
            if (isSeasonTicketInUse(ticketId) == true){
                throw new RuntimeException("season ticket associated with ticketId is currently in use");
        }
		seasonTicketDAO.recordTicketEntry(ticketId);
                
	}


/**
 * causes the current usage record of the season ticket associated with ticketID to be finalized.
 * @throws RuntimeException if the season ticket associated with ticketId does not exist, or is not currently in use 
 * @param ticketId 
 */
    @Override
	public void recordSeasonTicketExit(String ticketId) {
             if (seasonTicketDAO.findTicketById(ticketId) == null){
            throw new RuntimeException("season ticket associated with ticketId does not exist");
        }
            if (isSeasonTicketInUse(ticketId) == false){
                throw new RuntimeException("season ticket associated with ticketId is currently not in use");
        }
		seasonTicketDAO.recordTicketExit(ticketId);

}
     /**
     *Calculates whether or not the carpark is within business hours and days.
     * @return boolean
     */
        public boolean isBusinessHours(){
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            boolean businessHours = false;
            try {
            //sets opening business hours at 7am
            String stringOpeningTime = "07:00:00";
            Date openingTime = sdf.parse(stringOpeningTime);
            Calendar calenderOpeningTime = Calendar.getInstance();
            calenderOpeningTime.setTime(openingTime);

            //sets closing business hours at 7pm
            String stringClosingTime = "19:00:00";
            Date closingTime = sdf.parse(stringClosingTime);
            Calendar calenderClosingTime = Calendar.getInstance();
            calenderClosingTime.setTime(closingTime);

            //sets current time
            Calendar calendarCurrentTime = Calendar.getInstance();
            String stringCurrentTime = sdf.format(calendarCurrentTime.getTime());
            Date currentTime = sdf.parse(stringCurrentTime);
            calendarCurrentTime.setTime(currentTime);

            //tests if current time is between opening time and closing time
            Date current = calendarCurrentTime.getTime();
            if (current.after(calenderOpeningTime.getTime()) && (current.before(calenderClosingTime.getTime()))) {
                businessHours = true;
            }
        } catch (ParseException e) {
            e.printStackTrace();
            }
                
            Calendar c = Calendar.getInstance();
            //Retrieves current day as integer from 1-7 Sunday =1, Saturday = 7
            int day= c.get(Calendar.DAY_OF_WEEK);     
                
            return ((businessHours == true) && (day >= 2) && (day <= 6));
      }

        @SuppressWarnings("deprecation")
		public float calcCharge(long start, long end) {
            //create Date objects with given long values
            Date startTime = new Date(start);
            Date endTime = new Date(end);

            int daysBetweenDates = daysBetween(startTime, endTime);
            int curDayBetween = 0;
            
            int curDayOfWeek = startTime.getDay();
            
            System.out.println("days between: " + daysBetweenDates);
            
            //initialize float = 0, currentStartTime = startTime
            float charge = 0;
            Date curStartTime = new Date(startTime.getTime());           
            
            //run while look as long as currentDay does not = endDay
            while (curDayBetween != daysBetweenDates) {
                Date curEndTime = new Date(startTime.getTime());   //set endDay time to midnight. 
                curEndTime.setHours(23);
                curEndTime.setMinutes(59);
                curEndTime.setSeconds(59);
                
                //if the start time is midnight, then have to set all values to 0. 
                if (curStartTime.getHours() == 23 && curStartTime.getMinutes() == 59 && curStartTime.getSeconds() == 59) {
                		curStartTime.setHours(0);
                		curStartTime.setMinutes(0);
                		curStartTime.setSeconds(0);
                }
                //call calcDayCharge method, passing in current values. 
                charge += calcDayCharge(curStartTime, curEndTime, curDayOfWeek);
                //reset currentStartTime to endTime
                curStartTime = new Date(curEndTime.getTime());
                //increment day, check if passed into new week
                curDayBetween++;
                curDayOfWeek++;
                if (curDayOfWeek == 7) {
                    curDayOfWeek = 0;
                }
            }
            //if current day is the same as end day, reset midnight to 0 values. 
            if (curStartTime.getHours() == 23 && curStartTime.getMinutes() == 59 && curStartTime.getSeconds() == 59) {
        		curStartTime.setHours(0);
        		curStartTime.setMinutes(0);
        		curStartTime.setSeconds(0);
        }
            //call calc method. 
            charge += calcDayCharge(curStartTime, endTime, curDayOfWeek);
            //return accumulated charge
            return charge;
        }

        //calcDayCharge checks for BH and OOH and determines correct charge
        @SuppressWarnings("deprecation")
		private float calcDayCharge(Date startDate, Date endDate, int day) {
            
            //create time objets from given Date objects
            Time startTime = new Time(startDate.getHours(), startDate.getMinutes(), startDate.getSeconds());
            Time endTime = new Time(endDate.getHours(), endDate.getMinutes(), endDate.getSeconds());
            //create Business Hours Time Objects
            Time startBH = new Time(7, 0, 0);
            Time endBH = new Time(19, 0, 0);

            //initialize dayCharge
            float dayCharge = (float) 0.0;
            //check if it is business day
            if (isBusinessDay(day)) { 
                
                //if isBusiness Day and all Out of Hours
                if (endTime.before(startBH) || startTime.after(endBH)) {
                    dayCharge = (float) (((getMinutes(endTime) - getMinutes(startTime))/60.0) * OOH_RATE);
                    dayCharge = (float) (Math.round(dayCharge * 100.0) / 100.0);
                }
                //if isBusiness Day and all in Business Hours
                else if (startTime.after(startBH) && endTime.before(endBH)) {
                    dayCharge = (float) (((getMinutes(endTime) - getMinutes(startTime))/60.0) * BH_RATE);
                    dayCharge = (float) (Math.round(dayCharge * 100.0) / 100.0);
                }
                //if isBusiness Day and Out of Hours start / Business Hours end
                else if (startTime.before(startBH) && endTime.before(endBH)) {
                    dayCharge = (float) (((getMinutes(startBH) - getMinutes(startTime))/60.0) * OOH_RATE);
                    dayCharge += ((getMinutes(endTime) - getMinutes(startBH))/60.0) * BH_RATE;
                    dayCharge = (float) (Math.round(dayCharge * 100.0) / 100.0);
                }
                //if isBusinessDay and Business Hours start / Out of Hours end
                else if (startTime.after(startBH) && startTime.before(endBH) && endTime.after(endBH)) {
                    dayCharge = (float) (((getMinutes(endBH) - getMinutes(startTime))/60.0) * BH_RATE);
                    dayCharge += ((getMinutes(endTime) - getMinutes(endBH))/60.0) * OOH_RATE;
                    dayCharge = (float) (Math.round(dayCharge * 100.0) / 100.0);
                }
                //if isBusiness Day Out of Hours start / through Business Hours / Out of Hours end
                else if (startTime.before(startBH) && endTime.after(endBH)) {
                    dayCharge = (float) (((getMinutes(startBH) - getMinutes(startTime))/60.0) * OOH_RATE);
                    dayCharge += ((getMinutes(endBH) - getMinutes(startBH))/60.0) * BH_RATE;
                    dayCharge += ((getMinutes(endTime) - getMinutes(endBH))/60.0) * OOH_RATE;
                    dayCharge = (float) (Math.round(dayCharge * 100.0) / 100.0);
                }
                else {
                    //else time error
                    System.out.println("time error");
                }
            }
            //else not Busines Day, all Out of Hours
            else {
                dayCharge = (float) (((getMinutes(endTime) - getMinutes(startTime))/60.0) * OOH_RATE);
                dayCharge = (float) (Math.round(dayCharge * 100.0) / 100.0);
                System.out.println(dayCharge);
            }
            //return dayCharge
            return dayCharge;
        }
        
        //isBusinessDay() takes int and returns true or false for Business Day
        private boolean isBusinessDay(int day) {
            if (day > 0 && day < 5) {
                return true;
            }
            else
                return false;
        }
        
        //getMinutes() takes a Time object and returns the amount of total minutes. Calculated from hours, minutes and seconds
        @SuppressWarnings("deprecation")
        private int getMinutes(Time time) {
            int minutes = 0;
            minutes += time.getMinutes();
            minutes += (time.getHours() * 60);
            if (time.getSeconds() >= 30) {
            	minutes++;
            } 
            return minutes;
        }
        
        private int daysBetween(Date d1, Date d2) {
        	Calendar startCal = new GregorianCalendar();
        	Calendar endCal = new GregorianCalendar();
        	
        	startCal.setTime(d1);
        	endCal.setTime(d2);
                
                int currentYear = startCal.get(Calendar.YEAR);
                int endYear = endCal.get(Calendar.YEAR);
                
                int days = 0;
                
               while (currentYear != endYear) {
                   Calendar lastDayOfYear = new GregorianCalendar(currentYear, 11, 31);
                   int dayDecember31 = lastDayOfYear.get(Calendar.DAY_OF_YEAR);
                   
                   days += dayDecember31 - (startCal.get(Calendar.DAY_OF_YEAR));
                   
                   currentYear++;
                   startCal.set(currentYear, 0, 1);  //set current to Jan 1st of next year.            
               }
        	
        	days += (endCal.get(Calendar.DAY_OF_YEAR)) - (startCal.get(Calendar.DAY_OF_YEAR));

        	return days;
        	
        	}
        

}
