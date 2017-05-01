package com.example.mobile.typinganalyzer;


import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static android.os.SystemClock.elapsedRealtime;
import static java.lang.StrictMath.abs;

public class KeyboardAnalyzer {
    private String curWord;
    private ArrayList<Long> curTimings;
    private Set<String> wordSet;
    private TimeProfileDbHelper dbHelper;
    private SharedPreferences prefs;
    private long timer;
    private double score;
    private String scoreID = "score";
    private Context context;
    private long id;


    public KeyboardAnalyzer(Context context) {
        this.context = context;
        curWord = new String();
        curTimings = new ArrayList<>();
        dbHelper = new TimeProfileDbHelper(context);
        prefs = context.getSharedPreferences(context.getString(R.string.prefs_key), Context.MODE_PRIVATE);
        score = Double.valueOf(prefs.getString(scoreID, "0"));
        wordSet = new HashSet<>();
        timer = elapsedRealtime();

        // put all the words in a set for quick access later
        String[] wordList = context.getResources().getStringArray(R.array.words);
        for (int i = 0; i < wordList.length; ++i) {
            wordSet.add(wordList[i]);
        }
    }

    /* The only function needed for interfacing with this class. This function take a character
     * (presumably the next character in the text) and the duration of the key press. Alphanumeric,
     * space, and backspace are only considered; everything else is ignored for simplicity. Words
     * are delimited with space and backspace. Once a word is formed, it is pushed on for further
     * analysis (checked with the database).
     */
    public void push_char(char c, long duration) {
        long latency = 0;
        if (curWord.length() > 0) {
            latency = getLatency(duration);
        }
        if (c == ' ') {
            // check if the word is in the database and integrate the timings
            updateEntry();
            // reset the word and the timings
            resetWord();
        } else if (c == 8) {
            resetWord();
        } else if ((c > 64 && c < 91) || (c > 96 && c < 123)) {
            if (curWord.length() > 0) {
                curTimings.add(latency);
            }
            curTimings.add(duration);
            curWord = curWord.concat(String.valueOf(c));
        }
    }

    private long getLatency(long duration) {
        long newTimer = elapsedRealtime();
        long latency = newTimer - timer - duration;
        if (latency < 0) {
            latency = 0;
        }
        timer = newTimer;
        return latency;
    }

    private void resetWord() {
        curWord = "";
        curTimings.clear();
    }

    private double updateMean(double curMean, double value, int curCount) {
        return curMean + (value - curMean)/(curCount + 1);
    }

    private double updateVar(double curVar, double curMean, double value, int curCount) {
        return ((double)curCount - 1)/(double)curCount*curVar + (value - curMean)*(value - curMean)/(curCount + 1);
    }

    /* Checks if the timings for this word are good or not. It goes through each timing and figures
     * out whether it falls in the threshold or not. It keeps track of how many timings are good and
     * checks for the overall goodness. If the timings are bad, then the score is increases
     * proportional to the square root of the length of the word. Once the score is high enough,
     * some sort of alert will be given to user.
     */
    private void checkWord(ArrayList<Double> timings) {
        int length = curWord.length();
        int miniScore = 0;
        for (int i = 0; i < 4*length - 2; i += 2) {
            long value = curTimings.get(i>>1);
            double avg = timings.get(i);
            double stdDev = Math.sqrt(timings.get(i + 1));
            if (abs(value - avg) > 0.75*stdDev) {
                miniScore++;
            }
        }
        int num, dom;
        if (length == 1) {
            num = 1;
            dom = 1;
        } else if (length == 2) {
            num = 2;
            dom = 3;
        } else {
            num = 3;
            dom = 4;
        }
        if (dom*miniScore < num*(2*length - 1)) {
            score += Math.sqrt(length);

        } else {
            score -= Math.sqrt(length);
            if (score < 0) {
                score = 0;
            }
        }
        checkScore();
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(scoreID, String.valueOf(score));
        editor.commit();
    }

    private void checkScore() {
        //Log.i("score", String.valueOf(score));
        Toast.makeText(context, String.valueOf(score), Toast.LENGTH_SHORT).show();
        if (score > 50) {
            Log.i("score", "The score is over 50");
            score = 0;
        }
    }

    private void updateEntry() {
        // check if word is in the word set
        String word = curWord.toLowerCase();
        if (wordSet.contains(word)) {
            Log.i("updateEntry", word);
            SQLiteDatabase dbRead = dbHelper.getReadableDatabase();
            SQLiteDatabase dbWRite = dbHelper.getWritableDatabase();

            String selection = TimeProfileContract.TimeProfile.C_WORD + " = ?";
            String[] selectionArgs = {word};
            Cursor cursor = dbRead.query(TimeProfileContract.TimeProfile.TABLE_NAME, null, selection, selectionArgs, null, null, null);

            // check if the query returned anything, yes: update, no: add row
            int length = word.length();
            ContentValues row = new ContentValues();
            if (cursor.getCount() > 0) {
                Toast.makeText(context, "updating row", Toast.LENGTH_SHORT).show();
                cursor.moveToNext();
                ArrayList<Double> timings = new ArrayList<>();
                int count = cursor.getInt(cursor.getColumnIndexOrThrow(TimeProfileContract.TimeProfile.C_COUNT));
                for (int i = 0; i < length - 1; ++i) {
                    timings.add(cursor.getDouble(cursor.getColumnIndexOrThrow("d" + String.valueOf(i) + "_avg")));
                    timings.add(cursor.getDouble(cursor.getColumnIndexOrThrow("d" + String.valueOf(i) + "_var")));
                    timings.add(cursor.getDouble(cursor.getColumnIndexOrThrow("l" + String.valueOf(i) + "_avg")));
                    timings.add(cursor.getDouble(cursor.getColumnIndexOrThrow("l" + String.valueOf(i) + "_var")));
                }
                timings.add(cursor.getDouble(cursor.getColumnIndexOrThrow("d" + String.valueOf(length - 1) + "_avg")));
                timings.add(cursor.getDouble(cursor.getColumnIndexOrThrow("d" + String.valueOf(length - 1) + "_var")));

                // check if there are enough samples for a good test and then check if the word timings are good
                if (count > 1) {
                    Toast.makeText(context, "Got word " + word, Toast.LENGTH_SHORT).show();
                    checkWord(timings);
                }

                // get previous values and update with new ones
                for (int i = 0; i < 4*length - 2; i += 2) {
                    double avg = timings.get(i);
                    double var = timings.get(i + 1);
                    long value = curTimings.get(i>>1);
                    double newAvg = updateMean(avg, value, count);
                    double newVar = updateVar(var, avg, value, count);
                    if ((i>>1)%2 == 0) { // i/2 is odd
                        row.put("d" + String.valueOf(i>>2) + "_avg", newAvg);
                        row.put("d" + String.valueOf(i>>2) + "_var", newVar);
                    } else {
                        row.put("l" + String.valueOf(i>>2) + "_avg", newAvg);
                        row.put("l" + String.valueOf(i>>2) + "_var", newVar);
                    }
                }
                row.put(TimeProfileContract.TimeProfile.C_COUNT, ++count);
                dbRead.update(TimeProfileContract.TimeProfile.TABLE_NAME, row, "_id=" + cursor.getLong(cursor.getColumnIndex(TimeProfileContract.TimeProfile._ID)), null);
            } else {
                // insert new word into database
                Toast.makeText(context, "inserting row", Toast.LENGTH_SHORT).show();
                row.put(TimeProfileContract.TimeProfile.C_WORD, word);
                for (int i = 0; i < 4*length - 2; i += 2) {
                    if ((i>>1)%2 == 0) { // i/2 is even
                        row.put("d" + String.valueOf(i>>2) + "_avg", curTimings.get(i>>1));
                        row.put("d" + String.valueOf(i>>2) + "_var", 0);
                    } else {
                        row.put("l" + String.valueOf(i>>2) + "_avg", curTimings.get(i>>1));
                        row.put("l" + String.valueOf(i>>2) + "_var", 0);
                    }
                }
                row.put(TimeProfileContract.TimeProfile.C_COUNT, 1);
                id = dbWRite.insert(TimeProfileContract.TimeProfile.TABLE_NAME, null, row);
                Cursor c = dbRead.query(TimeProfileContract.TimeProfile.TABLE_NAME, null, TimeProfileContract.TimeProfile._ID + " = ?", new String[] {String.valueOf(id)}, null, null, null, null);
                if (c != null) {
                    c.moveToFirst();
                    Toast.makeText(context, "checking row is good", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

}
