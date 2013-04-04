package com.badlogic.gdx.graphics.g3d.shaders;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL10;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.g3d.Light;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.materials.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.materials.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.materials.Material;
import com.badlogic.gdx.graphics.g3d.materials.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.utils.RenderContext;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxRuntimeException;

public class DefaultShader implements Shader {
	public final static String PROJECTION_TRANSFORM = "u_projTrans";
	public final static String MODEL_TRANSFORM = "u_modelTrans";
	public final static String NORMAL_TRANSFORM = "u_normalMatrix";
	
	private static String defaultVertexShader = null;
	public final static String getDefaultVertexShader() {
		if (defaultVertexShader == null)
			defaultVertexShader = Gdx.files.classpath("com/badlogic/gdx/graphics/g3d/shaders/default.vertex.glsl").readString();
		return defaultVertexShader;
	}
	
	private static String defaultFragmentShader = null;
	public final static String getDefaultFragmentShader() {
		if (defaultFragmentShader == null)
			defaultFragmentShader = Gdx.files.classpath("com/badlogic/gdx/graphics/g3d/shaders/default.fragment.glsl").readString();
		return defaultFragmentShader;
	}

	protected static long implementedFlags = BlendingAttribute.Type | TextureAttribute.Diffuse | ColorAttribute.Diffuse;
	public static boolean ignoreUnimplemented = true;
	
	protected final ShaderProgram program;
	protected int projTransLoc;
	protected int modelTransLoc;
	protected int normalTransLoc;
	protected int diffuseTextureLoc;
	protected int diffuseColorLoc;
	protected int ambientLoc;
	protected int lightsLoc;
	protected int lightSize;
	protected int lightTypeOffset;
	protected int lightColorOffset;
	protected int lightPositionOffset;
	protected int lightAttenuationOffset;
	protected int lightDirectionOffset;
	protected int lightAngleOffset;
	protected int lightExponentOffset;
	
	private final Light[] currentLights;
	private final Color ambient = new Color(0,0,0,1);
	
	protected RenderContext context;
	protected long mask;
	
	public DefaultShader(final Material material, int maxLightsCount) {
		this(getDefaultVertexShader(), getDefaultFragmentShader(), material, maxLightsCount);
	}
	
	public DefaultShader(final long mask, int maxLightsCount) {
		this(getDefaultVertexShader(), getDefaultFragmentShader(), mask, maxLightsCount);
	}

	public DefaultShader(final String vertexShader, final String fragmentShader, final Material material, int maxLightsCount) {
		this(vertexShader, fragmentShader, material.getMask(), maxLightsCount);
	}
	
	public DefaultShader(final String vertexShader, final String fragmentShader, final long mask, int maxLightsCount) {
		if (!Gdx.graphics.isGL20Available())
			throw new GdxRuntimeException("This shader requires OpenGL ES 2.0");
		
		currentLights = maxLightsCount < 0 ? null : new Light[maxLightsCount];
		if (currentLights != null)
			for (int i = 0; i < currentLights.length; i++)
				currentLights[i] = new Light();
		
		String prefix = "";
		this.mask = mask;
		
		if (!ignoreUnimplemented && (implementedFlags & mask) != mask)
			throw new GdxRuntimeException("Some attributes not implemented yet ("+mask+")");
		
		if (maxLightsCount > 0)
			prefix += "#define lightsCount "+maxLightsCount+"\n";
		if ((mask & BlendingAttribute.Type) == BlendingAttribute.Type)
			prefix += "#define "+BlendingAttribute.Alias+"Flag\n";
		if ((mask & TextureAttribute.Diffuse) == TextureAttribute.Diffuse)
			prefix += "#define "+TextureAttribute.DiffuseAlias+"Flag\n";
		if ((mask & ColorAttribute.Diffuse) == ColorAttribute.Diffuse)
			prefix += "#define "+ColorAttribute.DiffuseAlias+"Flag\n";

		program = new ShaderProgram(prefix + vertexShader, prefix + fragmentShader);
		if (!program.isCompiled())
			throw new GdxRuntimeException(program.getLog());
		
		projTransLoc = program.getUniformLocation(PROJECTION_TRANSFORM);
		modelTransLoc = program.getUniformLocation(MODEL_TRANSFORM);
		normalTransLoc = program.getUniformLocation(NORMAL_TRANSFORM);
		diffuseTextureLoc = ((mask & TextureAttribute.Diffuse) != TextureAttribute.Diffuse) ? -1 : program.getUniformLocation(TextureAttribute.DiffuseAlias);
		diffuseColorLoc = ((mask & ColorAttribute.Diffuse) != ColorAttribute.Diffuse) ? -1 : program.getUniformLocation(ColorAttribute.DiffuseAlias);
		ambientLoc = maxLightsCount < 0 ? -1 : program.getUniformLocation("ambient");
		lightsLoc = maxLightsCount > 0 ? program.getUniformLocation("lights[0].type") : -1;
		lightSize = (lightsLoc >= 0 && maxLightsCount > 1) ? (program.getUniformLocation("lights[1].type") - lightsLoc) : -1;
		lightTypeOffset = 0;
		lightColorOffset = lightsLoc >= 0 ? program.getUniformLocation("lights[0].color") - lightsLoc : -1;
		lightPositionOffset = lightsLoc >= 0 ? program.getUniformLocation("lights[0].position") - lightsLoc : -1;
		lightAttenuationOffset = lightsLoc >= 0 ? program.getUniformLocation("lights[0].attenuation") - lightsLoc : -1;
		lightDirectionOffset = lightsLoc >= 0 ? program.getUniformLocation("lights[0].direction") - lightsLoc : -1;
		lightAngleOffset = lightsLoc >= 0 ? program.getUniformLocation("lights[0].angle") - lightsLoc : -1;
		lightExponentOffset = lightsLoc >= 0 ? program.getUniformLocation("lights[0].exponent") - lightsLoc : -1;
	}
	
	@Override
	public boolean canRender(final Renderable renderable) {
		return mask == renderable.material.getMask() && (renderable.lights == null) == (currentLights == null);
	}
	
	/*@Override
	public int compareTo (final Object other) {
		return (other instanceof RenderShader) ? compareTo((RenderShader)other) : -1;
	}*/
	
	@Override
	public int compareTo(Shader other) {
		// TODO Auto-generated method stub
		return 0;
	}
	
	@Override
	public boolean equals (Object obj) {
		return (obj instanceof DefaultShader) ? equals((DefaultShader)obj) : false;
	}
	
	public boolean equals (DefaultShader obj) {
		return (obj == this);
	}

	private Mesh currentMesh;
	private Matrix4 currentTransform;
	private Matrix3 normalMatrix = new Matrix3();
	private Camera camera;
	
	@Override
	public void begin (final Camera camera, final RenderContext context) {
		this.context = context;
		this.camera = camera;
		program.begin();
		context.setDepthTest(true, GL10.GL_LEQUAL);
		program.setUniformMatrix(projTransLoc, camera.combined);
		if (currentLights != null)
			for (int i = 0; i < currentLights.length; i++)
				currentLights[i].set(null);
		ambient.set(0,0,0,0);
	}

	@Override
	public void render (final Renderable renderable) {
		if (!renderable.material.has(BlendingAttribute.Type))
			context.setBlending(false, GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);
		if (currentTransform != renderable.transform) {
			program.setUniformMatrix(modelTransLoc, currentTransform = renderable.transform);
			program.setUniformMatrix(normalTransLoc, normalMatrix.set(currentTransform));
		}
		bindMaterial(renderable);
		if (currentLights != null)
			bindLights(renderable);
		if (currentMesh != renderable.mesh) {
			if (currentMesh != null)
				currentMesh.unbind(program);
			(currentMesh = renderable.mesh).bind(program);
		}
		renderable.mesh.render(program, renderable.primitiveType, renderable.meshPartOffset, renderable.meshPartSize);
	}

	@Override
	public void end () {
		if (currentMesh != null) {
			currentMesh.unbind(program);
			currentMesh = null;
		}
		currentTransform = null;
		currentTextureAttribute = null;
		currentMaterial = null;
		program.end();
	}
	
	Material currentMaterial;
	private final void bindMaterial(final Renderable renderable) {
		if (currentMaterial == renderable.material)
			return;
		currentMaterial = renderable.material;
		for (Material.Attribute attr : currentMaterial) {
			final long t = attr.type;
			if (BlendingAttribute.is(t))
				context.setBlending(true, ((BlendingAttribute)attr).sourceFunction, ((BlendingAttribute)attr).destFunction);
			else if (ColorAttribute.is(t)) {
				ColorAttribute col = (ColorAttribute)attr;
				if ((t & ColorAttribute.Diffuse) == ColorAttribute.Diffuse)
					program.setUniformf(diffuseColorLoc, col.color);
				// TODO else if (..)
			}
			else if (TextureAttribute.is(t)) {
				TextureAttribute tex = (TextureAttribute)attr;
				if ((t & TextureAttribute.Diffuse) == TextureAttribute.Diffuse)
					bindTextureAttribute(diffuseTextureLoc, tex);
				// TODO else if (..)
			}  
			else {
				if(!ignoreUnimplemented) {
					throw new GdxRuntimeException("unknown attribute");
				}
			}
		}
	}

	TextureAttribute currentTextureAttribute;
	private final void bindTextureAttribute(final int uniform, final TextureAttribute attribute) {
		final int unit = context.textureBinder.bind(attribute.textureDescription);
		program.setUniformi(uniform, unit);
		currentTextureAttribute = attribute;
	}
	
	private final void bindLights(final Renderable renderable) {
		int idx = -1;
		ambient.set(0,0,0,1);
		if (renderable.lights != null) {
			for (final Light light : renderable.lights) {
				if (light.type == Light.AMBIENT)
					ambient.add(light.color);
				else if (lightsLoc >= 0 && ++idx < currentLights.length && !currentLights[idx].equals(light)) {
					final int loc = lightsLoc + idx * lightSize;
					currentLights[idx].set(light);
					program.setUniformi(loc + lightTypeOffset, light.type);
					program.setUniformf(loc + lightColorOffset, light.color);
					program.setUniformf(loc + lightPositionOffset, light.position);
					program.setUniformf(loc + lightAttenuationOffset, light.attenuation);
					program.setUniformf(loc + lightDirectionOffset, light.direction);
					program.setUniformf(loc + lightAngleOffset, MathUtils.cosDeg(light.angle));
					program.setUniformf(loc + lightExponentOffset, light.exponent);
				}
			}
		}
		if (lightsLoc >= 0) {
			while (++idx < currentLights.length){
				if (currentLights[idx].type != Light.NONE) {
					program.setUniformi(lightsLoc + idx * lightSize + lightTypeOffset, Light.NONE);
					currentLights[idx].type = Light.NONE;
				}
			}
		}
		if (ambientLoc >= 0)
			program.setUniformf(ambientLoc, ambient);
		/* for (int i = 0; i < currentLights.length; i++) {
			final int loc = lightsLoc + i * lightSize;
			if (renderable.lights.length <= i) {
				if (currentLights[i].type != Light.NONE) {
					program.setUniformf(loc + lightPowerOffset, 0f);
					currentLights[i].type = Light.NONE;
				}
			}
			else {
				if (!currentLights[i].equals(renderable.lights[i])) {
					program.setUniformf(loc, renderable.lights[i].color);
					program.setUniformf(loc + lightPositionOffset, renderable.lights[i].position);
					program.setUniformf(loc + lightPowerOffset, renderable.lights[i].power);
					currentLights[i].set(renderable.lights[i]);
				}
			}
		} */
	}

	@Override
	public void dispose () {
		program.dispose();
	}
}
