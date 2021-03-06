/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package integration_test.bcccp.carpark.Paystation;

import bcccp.carpark.*;
import bcccp.carpark.paystation.*;
import bcccp.tickets.adhoc.*;
import bcccp.tickets.season.*;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import org.junit.Test;
import org.mockito.Mockito;
/**
 *
 * @author PeacheyMacbook
 */
public class Integration_Carpark_PaystationUseCase {
    
    IAdhocTicketDAO adhocdao;
    ISeasonTicketDAO seasondao;
    ICarpark carpark;
    IPaystationUI mockUI;
    IPaystationController payController;
    IAdhocTicket ticket;
    
    @Test
    public void testTicketInserted() {
        //set up DAO's and Carpark
        adhocdao = new AdhocTicketDAO(new AdhocTicketFactory());
        seasondao = new SeasonTicketDAO(new UsageRecordFactory());
        carpark = new Carpark("test carpark", 10, 1, adhocdao, seasondao);

        //set up mock Paystation UI, 
        mockUI = mock(PaystationUI.class);

        //paystation controller
        payController = new PaystationController(carpark, mockUI);

        ticket = carpark.issueAdhocTicket();
        ticket.enter(System.currentTimeMillis());

        payController.ticketInserted(ticket.getBarcode());

        verify(mockUI).display(Mockito.contains("Pay ")); 
        verify(mockUI, never()).beep();

    }
    
    
    @Test
    public void testTicketPaid() throws InterruptedException {
        //set up DAO's and Carpark
        adhocdao = new AdhocTicketDAO(new AdhocTicketFactory());
        seasondao = new SeasonTicketDAO(new UsageRecordFactory());
        carpark = new Carpark("test carpark", 10, 1, adhocdao, seasondao);

        //set up mock Paystation UI, 
        mockUI = mock(PaystationUI.class);

        //paystation controller
        payController = new PaystationController(carpark, mockUI);

        ticket = carpark.issueAdhocTicket();
        ticket.enter(System.currentTimeMillis());
        
        payController.ticketInserted(ticket.getBarcode());
        
        //Implemented second delay to avoid paidTime equalling entryTime
        TimeUnit.SECONDS.sleep(1);
        
        payController.ticketPaid();
        
        assertTrue(ticket.getPaidDateTime() > 0);
        verify(mockUI, never()).beep();
    }
    
}
