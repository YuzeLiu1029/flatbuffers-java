package example;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import io.grpc.*;
import io.grpc.stub.StreamObserver;
import org.junit.Test;

import com.google.flatbuffers.FlatBufferBuilder;

public class ExampleTest {
	static Map<String, Monster> monsters = new HashMap<String, Monster>();
	static final String BIG_MONSTER_NAME = "big-monster";
	static {
		monsters.put(BIG_MONSTER_NAME, makeBigMonster());
	}
	static Monster makeBigMonster() {
		FlatBufferBuilder builder = new FlatBufferBuilder(0);

		// Create some weapons for our Monster ('Sword' and 'Axe').
		int weaponOneName = builder.createString("Sword");
		short weaponOneDamage = 3;
		int weaponTwoName = builder.createString("Axe");
		short weaponTwoDamage = 5;

		// Use the `createWeapon()` helper function to create the weapons, since we set every field.
		int[] weaps = new int[2];
		weaps[0] = Weapon.createWeapon(builder, weaponOneName, weaponOneDamage);
		weaps[1] = Weapon.createWeapon(builder, weaponTwoName, weaponTwoDamage);

		// Serialize the FlatBuffer data.
		int name = builder.createString("Orc");
		byte[] treasure = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
		int inv = Monster.createInventoryVector(builder, treasure);
		int weapons = Monster.createWeaponsVector(builder, weaps);
		int pos = Vec3.createVec3(builder, 1.0f, 2.0f, 3.0f);

		Monster.startMonster(builder);
		Monster.addPos(builder, pos);
		Monster.addName(builder, name);
		Monster.addColor(builder, Color.Red);
		Monster.addHp(builder, (short)300);
		Monster.addInventory(builder, inv);
		Monster.addWeapons(builder, weapons);
		Monster.addEquippedType(builder, Equipment.Weapon);
		Monster.addEquipped(builder, weaps[1]);
		int orc = Monster.endMonster(builder);

		builder.finish(orc); // You could also call `Monster.finishMonsterBuffer(builder, orc);`.

		// We now have a FlatBuffer that can be stored on disk or sent over a network.
		ByteBuffer buf = builder.dataBuffer();

		// Get access to the root:
		return Monster.getRootAsMonster(buf);
	}

	static class MyService extends MonsterSvcGrpc.MonsterSvcImplBase {
		@Override
		public void showMonster(MonsterRequest request, StreamObserver<Monster> responseObserver) {
			Monster monster = monsters.get(request.name());
			if (monster != null) {
				responseObserver.onNext(monster);
				responseObserver.onCompleted();
			} else {
				responseObserver.onError(Status.NOT_FOUND.asRuntimeException());
			}
		}
	};

	static int startServer() throws IOException {
		Server server = ServerBuilder.forPort(0).addService(new MyService()).build().start();
		return server.getPort();
	}


	@Test
	public void test() throws IOException {
		int port = startServer();
		ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", port)
				// Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
				// needing certificates.
				.usePlaintext(true)
				.directExecutor()
				.build();

		MonsterSvcGrpc.MonsterSvcBlockingStub stub = MonsterSvcGrpc.newBlockingStub(channel);

		FlatBufferBuilder builder = new FlatBufferBuilder();
		int offsetStr1 = builder.createString(BIG_MONSTER_NAME);
		int offsetRequest = MonsterRequest.createMonsterRequest(builder, offsetStr1);
		builder.finish(offsetRequest);
		ByteBuffer buffer = builder.dataBuffer();
		MonsterRequest monsterRequest = MonsterRequest.getRootAsMonsterRequest(buffer);

		Monster monster = stub.showMonster(monsterRequest);

		// Note: We did not set the `mana` field explicitly, so we get back the default value.
	    assert monster.mana() == (short)150;
	    assert monster.hp() == (short)300;
	    assert monster.name().equals("Orc");
	    assert monster.color() == Color.Red;
	    assert monster.pos().x() == 1.0f;
	    assert monster.pos().y() == 2.0f;
	    assert monster.pos().z() == 3.0f;

	    // Get and test the `inventory` FlatBuffer `vector`.
	    for (int i = 0; i < monster.inventoryLength(); i++) {
	      assert monster.inventory(i) == (byte)i;
	    }

	    // Get and test the `weapons` FlatBuffer `vector` of `table`s.
	    String[] expectedWeaponNames = {"Sword", "Axe"};
	    int[] expectedWeaponDamages = {3, 5};
	    for (int i = 0; i < monster.weaponsLength(); i++) {
	      assert monster.weapons(i).name().equals(expectedWeaponNames[i]);
	      assert monster.weapons(i).damage() == expectedWeaponDamages[i];
	    }

	    // Get and test the `equipped` FlatBuffer `union`.
	    assert monster.equippedType() == Equipment.Weapon;
	    Weapon equipped = (Weapon)monster.equipped(new Weapon());
	    assert equipped.name().equals("Axe");
	    assert equipped.damage() == 5;

	    System.out.println("The FlatBuffer was successfully created and sent over the network, and verified!");
	}

}
