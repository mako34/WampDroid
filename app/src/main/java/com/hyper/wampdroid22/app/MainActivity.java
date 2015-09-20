//arregla

package com.hyper.wampdroid22.app;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import rx.Scheduler;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import ws.wamp.jawampa.*;
import ws.wamp.jawampa.connection.IWampConnectorProvider;
import ws.wamp.jawampa.transport.netty.NettyWampClientConnectorProvider;

public class MainActivity extends ActionBarActivity {

    WampClient client;
    Subscription addProcSubscription;
    Subscription counterPublication;
    Subscription onHelloSubscription;
    Scheduler rxScheduler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        IWampConnectorProvider connectorProvider = new NettyWampClientConnectorProvider();
        WampClientBuilder builder = new WampClientBuilder();


        // Scheduler for this example
        ExecutorService executor = Executors.newSingleThreadExecutor();
        rxScheduler = Schedulers.from(executor);

        Log.d("mensa", "comenzo");



        try {
            builder.withUri("ws://127.0.0.1:8080/ws")
                    .withRealm("realm1")
                    .withInfiniteReconnects()
                    .withCloseOnErrors(true)
                    .withReconnectInterval(5, TimeUnit.SECONDS)
                    .withConnectorProvider(connectorProvider);
            client = builder.build();

            Log.d("mensa", "builder");


        } catch (WampError e) {
            e.printStackTrace();
            return;
        } catch (Exception e) {
            e.printStackTrace();
        }


        // Subscribe on the clients status updates
        client.statusChanged()
                .observeOn(rxScheduler)
                .subscribe(new Action1<WampClient.State>() {
                    @Override
                    public void call(WampClient.State t1) {

                        Log.d("mensa", "Session status changed to " + t1);



                        if (t1 instanceof WampClient.ConnectedState) {
                            Log.d("mensa", "connectedStateMethods call");

                            connectedStateMethods();

                        }
                        else if (t1 instanceof WampClient.DisconnectedState) {
                            Log.d("mensa", "closeSubscriptions call");

                            closeSubscriptions();
                        }

                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable t) {
                        Log.d("mensa", "Session ended with error " + t);

                    }
                }, new Action0() {
                    @Override
                    public void call() {
                        Log.d("mensa", "Session ended normally");

                    }
                });

        client.open();

//Not needed
//        cuando finicta??
//        waitUntilKeypressed();


//        Log.d("mensa", "Shutting down");
//
//        closeSubscriptions();
//        client.close();
//        try {
//            client.getTerminationFuture().get();
//        } catch (Exception e) {}
//
//        executor.shutdown();
    }

    void connectedStateMethods() {


        final int TIMER_INTERVAL = 1000; // 1s
        final int[] counter = {0};

        // SUBSCRIBE to a topic and receive events
        onHelloSubscription  = client.makeSubscription("com.example.onhello", String.class)
                .observeOn(rxScheduler)
                .subscribe(new Action1<String>() {
                    @Override
                    public void call(String msg) {
                        Log.d("mensa","event for 'onhello' received: " + msg);
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable e) {
                        Log.d("mensa","failed to subscribe 'onhello': " + e);

                    }
                }, new Action0() {
                    @Override
                    public void call() {
                        Log.d("mensa","'onhello' subscription ended");

                    }
                });

        // REGISTER a procedure for remote calling
        addProcSubscription  = client.registerProcedure("com.example.add4")
                .observeOn(rxScheduler)
                .subscribe(new Action1<Request>() {
                    @Override
                    public void call(Request request) {
                        if (request.arguments() == null || request.arguments().size() != 2
                                || !request.arguments().get(0).canConvertToLong()
                                || !request.arguments().get(1).canConvertToLong())
                        {
                            try {
                                request.replyError(new ApplicationError(ApplicationError.INVALID_PARAMETER));
                            } catch (ApplicationError e) { }
                        }
                        else {
                            long a = request.arguments().get(0).asLong();
                            long b = request.arguments().get(1).asLong();
                            request.reply(a + b);
                        }
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable e) {
                        Log.d("mensa", "failed to register procedure: " + e);

                    }
                }, new Action0() {
                    @Override
                    public void call() {
                        Log.d("mensa", "procedure subscription ended");

                    }
                });

        // PUBLISH and CALL every second .. forever
        counter[0] = 0;
        counterPublication  = rxScheduler.createWorker().schedulePeriodically(new Action0() {
            @Override
            public void call() {
                // PUBLISH an event
                final int published = counter[0];
                client.publish("com.example.oncounter", published)
                        .observeOn(rxScheduler)
                        .subscribe(new Action1<Long>() {
                            @Override
                            public void call(Long t1) {
                                Log.d("mensa", "published to 'oncounter' with counter " + published);

                            }
                        }, new Action1<Throwable>() {
                            @Override
                            public void call(Throwable e) {
                                Log.d("mensa", "Error during publishing to 'oncounter': " + e);

                            }
                        });

                // CALL a remote procedure
                client.call("com.example.mul2", Long.class, counter[0], 3)
                        .observeOn(rxScheduler)
                        .subscribe(new Action1<Long>() {
                            @Override
                            public void call(Long result) {
                                Log.d("mensa", "mul2() called with result: " + result);

                            }
                        }, new Action1<Throwable>() {
                            @Override
                            public void call(Throwable e) {
                                boolean isProcMissingError = false;
                                if (e instanceof ApplicationError) {
                                    if (((ApplicationError) e).uri().equals("wamp.error.no_such_procedure"))
                                        isProcMissingError = true;
                                }
                                if (!isProcMissingError) {
                                    Log.d("mensa", "call of mul2() failed: " + e);

                                }
                            }
                        });

                counter[0]++;
            }
        }, TIMER_INTERVAL, TIMER_INTERVAL, TimeUnit.MILLISECONDS);


    }


    /**
     * Close all subscriptions (registered events + procedures)
     * and shut down all timers (doing event publication and calls)
     */
    void closeSubscriptions() {
        if (onHelloSubscription != null)
            onHelloSubscription.unsubscribe();
        onHelloSubscription = null;
        if (counterPublication != null)
            counterPublication.unsubscribe();
        counterPublication = null;
        if (addProcSubscription != null)
            addProcSubscription.unsubscribe();
        addProcSubscription = null;
    }


//
//    private void waitUntilKeypressed() {
//        try {
//            System.in.read();
//            while (System.in.available() > 0) {
//                System.in.read();
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }



    //




    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}