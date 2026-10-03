package com.descentmtb.physics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AirStyleTest {
    private BikeSim jump() {
        BikeSim s = new BikeSim(new BikeParams(), TestTerrains.flat(64,Terrain.Surface.DIRT));
        s.place(0,64,0,0);s.pos=s.pos.add(new V3(0,3,0));s.riderPos=s.riderPos.add(new V3(0,3,0));s.vel=s.riderVel=new V3(0,2,10);
        s.tick(Controls.NONE,.05);s.airBudget=1;return s;
    }
    @Test void tabletopUsesAirTweakWithoutTrickButton() {
        BikeSim s=jump();for(int i=0;i<8;i++)s.tick(new Controls(0,0,0,0,0,1,false,0,0),.05);
        assertTrue(s.airborne);assertTrue(s.maxTable>.9);assertEquals(com.descentmtb.trick.Trick.NONE,s.tricks.trick);
        for(int i=0;i<8;i++)s.tick(Controls.NONE,.05);
        assertTrue(Math.abs(s.lean)<.1,"release should bring the bike back under the rider");
    }
    @Test void whipCanBeThrownAndCountersteeredWithoutTrickButton() {
        BikeSim s=jump();for(int i=0;i<6;i++)s.tick(new Controls(1,0,0,0,0,0,false,0,0),.05);
        assertTrue(s.maxWhip>.55);
        double before=Math.abs(s.yaw);
        for(int i=0;i<6;i++)s.tick(new Controls(-1,0,0,0,0,0,false,0,0),.05);
        assertTrue(Math.abs(s.yaw)<before,"opposite stick should bring the whip back");
    }
    @Test void wallrideRequiresSpeedAndReleasesAsItSlows() {
        Terrain flat=TestTerrains.flat(64,Terrain.Surface.ROCK);
        Terrain wall=new Terrain(){
            public boolean ground(double x,double z,double a,double b,GroundHit out){return flat.ground(x,z,a,b,out);}
            public boolean solidAt(double x,double y,double z){return x<-.9&&y>64&&y<70||flat.solidAt(x,y,z);}
        };
        BikeSim s=new BikeSim(new BikeParams(),wall);s.place(-.35,64,0,0);s.pos=s.pos.add(new V3(0,2,0));s.riderPos=s.riderPos.add(new V3(0,2,0));s.vel=s.riderVel=new V3(0,0,12);
        boolean rode=false;for(int i=0;i<15;i++){s.tick(new Controls(0,0,0,0,0,1,false,0,0),.05);rode|=s.wallRide;}
        assertTrue(rode);assertTrue(Math.abs(s.lean)>1.1);assertFalse(s.bailed);
        s.vel=s.riderVel=new V3(0,0,4);s.tick(Controls.NONE,.05);assertFalse(s.wallRide);
    }
    @Test void softTyresSlowGraduallyAndHardTyresLoseGrip() {
        BikeParams soft=new BikeParams(),normal=new BikeParams(),hard=new BikeParams();
        BikeTuning.apply(soft,new BikeParams(),6,6,80,1);BikeTuning.apply(normal,new BikeParams(),26,28,80,1);BikeTuning.apply(hard,new BikeParams(),60,60,80,1);
        assertTrue(soft.tyreRolling>normal.tyreRolling*3);assertTrue(soft.tyreGrip>hard.tyreGrip);
        assertTrue(soft.pedalPower==normal.pedalPower,"pressure changes resistance, never silently disables pedalling");
    }
    @Test void oneFlatTyreCannotBeCancelledByAnOverinflatedTyre(){BikeParams p=new BikeParams();BikeTuning.apply(p,new BikeParams(),5,65,80,1);assertTrue(p.tyreRolling>2.9);}
    @Test void airbagAbsorbsHardLandingWithoutAnAutomaticBounce(){BikeSim s=new BikeSim(new BikeParams(),TestTerrains.flat(64,Terrain.Surface.AIRBAG));s.place(0,64,0,0);s.pos=s.pos.add(new V3(0,6,0));s.riderPos=s.riderPos.add(new V3(0,6,0));s.vel=s.riderVel=new V3(0,-14,5);double largest=0;for(int i=0;i<120;i++){s.tick(Controls.NONE,.05);if(s.front.contact||s.rear.contact)largest=Math.max(largest,s.vel.y);assertFalse(s.bailed,s.bailReason);assertTrue(s.pos.y>63.9);}assertTrue(largest<.5,"airbag must dissipate energy instead of bouncing");}
    @Test void aMovingDeckCarriesTheBrakedBike() {
        Terrain deck=new Terrain(){
            public boolean ground(double x,double z,double a,double b,GroundHit out){if(a<64||b>64)return false;out.set(64,V3.Y,Surface.WOOD);out.velocity=new V3(2,0,0);return true;}
            public boolean solidAt(double x,double y,double z){return y<64;}
        };
        BikeSim s=new BikeSim(new BikeParams(),deck);s.place(0,64,0,0);
        for(int i=0;i<60;i++)s.tick(new Controls(0,0,0,1,0,0,false,0,0),.05);
        assertTrue(s.pos.x>3,"support velocity should carry the bike");assertTrue(s.vel.x>1.7);
    }
}
